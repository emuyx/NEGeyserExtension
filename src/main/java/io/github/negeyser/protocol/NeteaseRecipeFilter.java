/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.CraftingRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.FurnaceRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.IdentifiableRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sanitizes recipes before they reach a NetEase client.
 *
 * <p>Geyser 2.12 generates recipes from its 26.x item/block registries. The NetEase client only
 * knows the 1.21.93/1.21.124/1.21.130 registries, and {@code SpoofedUpstream} removes newer items
 * from {@code ItemComponentPacket}. Any recipe that still references one of those removed items
 * (or a block id that was not remapped) can make the NetEase client crash while it parses the
 * recipe book. Such recipes are removed here; {@link UnlockedRecipesPacket} entries are filtered
 * against the recipes that were actually sent.</p>
 */
final class NeteaseRecipeFilter {

    record Result(int total, int dropped, Set<String> unknownItems, Set<String> droppedIds) {
    }

    private NeteaseRecipeFilter() {
    }

    /**
     * Removes recipes that reference items which do not exist in the NetEase client's registry
     * and remaps block definitions on recipe results. Surviving recipe ids are added to
     * {@code sentRecipeIds} so that recipe-book unlock packets can be filtered consistently.
     */
    static Result filter(CraftingDataPacket packet, NeteaseItemTranslator translator, Set<String> sentRecipeIds) {
        List<RecipeData> recipes = packet.getCraftingData();
        int total = recipes.size();
        int dropped = 0;
        Set<String> unknownItems = new LinkedHashSet<>();
        Set<String> droppedIds = new LinkedHashSet<>();

        for (int i = 0; i < recipes.size(); ) {
            RecipeData recipe = recipes.get(i);
            String unknown = firstUnknownItem(recipe, translator);
            if (unknown != null) {
                unknownItems.add(unknown);
                if (recipe instanceof IdentifiableRecipeData identifiable) {
                    droppedIds.add(identifiable.getId());
                }
                dropped++;
                recipes.remove(i);
                continue;
            }
            if (recipe instanceof IdentifiableRecipeData identifiable) {
                sentRecipeIds.add(identifiable.getId());
            }
            RecipeData remapped = translateResults(recipe, translator);
            if (remapped != recipe) {
                recipes.set(i, remapped);
            }
            i++;
        }
        return new Result(total, dropped, unknownItems, droppedIds);
    }

    /**
     * Drops recipe ids that were not registered with the client. Returns the number of removed
     * ids; if the packet ends up empty it must not be sent (an empty unlock list crashes clients).
     */
    static int filterUnlocked(UnlockedRecipesPacket packet, Set<String> sentRecipeIds) {
        if (packet == null || packet.getAction() == null
            || packet.getAction() == UnlockedRecipesPacket.ActionType.EMPTY
            || packet.getAction() == UnlockedRecipesPacket.ActionType.REMOVE_ALL) {
            return 0;
        }
        List<String> unlocked = packet.getUnlockedRecipes();
        int before = unlocked.size();
        unlocked.removeIf(id -> id == null || !sentRecipeIds.contains(id));
        return before - unlocked.size();
    }

    private static String firstUnknownItem(RecipeData recipe, NeteaseItemTranslator translator) {
        if (recipe instanceof CraftingRecipeData crafting) {
            String unknown = firstUnknownDescriptor(crafting.getIngredients(), translator);
            if (unknown != null) {
                return unknown;
            }
            return firstUnknownResult(crafting.getResults(), translator);
        }
        if (recipe instanceof SmithingTransformRecipeData smithing) {
            String unknown = firstUnknownDescriptor(smithing.getTemplate(), translator);
            if (unknown == null) {
                unknown = firstUnknownDescriptor(smithing.getBase(), translator);
            }
            if (unknown == null) {
                unknown = firstUnknownDescriptor(smithing.getAddition(), translator);
            }
            if (unknown == null) {
                unknown = firstUnknownItem(smithing.getResult(), translator);
            }
            return unknown;
        }
        if (recipe instanceof SmithingTrimRecipeData trim) {
            String unknown = firstUnknownDescriptor(trim.getTemplate(), translator);
            if (unknown == null) {
                unknown = firstUnknownDescriptor(trim.getBase(), translator);
            }
            if (unknown == null) {
                unknown = firstUnknownDescriptor(trim.getAddition(), translator);
            }
            return unknown;
        }
        if (recipe instanceof FurnaceRecipeData furnace) {
            return firstUnknownItem(furnace.getResult(), translator);
        }
        // Multi recipes and unknown future recipe types cannot reference item stacks.
        return null;
    }

    private static String firstUnknownResult(List<ItemData> results, NeteaseItemTranslator translator) {
        if (results == null) {
            return null;
        }
        for (ItemData item : results) {
            String unknown = firstUnknownItem(item, translator);
            if (unknown != null) {
                return unknown;
            }
        }
        return null;
    }

    private static String firstUnknownDescriptor(List<ItemDescriptorWithCount> ingredients, NeteaseItemTranslator translator) {
        if (ingredients == null) {
            return null;
        }
        for (ItemDescriptorWithCount ingredient : ingredients) {
            String unknown = firstUnknownDescriptor(ingredient, translator);
            if (unknown != null) {
                return unknown;
            }
        }
        return null;
    }

    private static String firstUnknownDescriptor(ItemDescriptorWithCount ingredient, NeteaseItemTranslator translator) {
        if (ingredient == null) {
            return null;
        }
        ItemDescriptor descriptor = ingredient.getDescriptor();
        if (descriptor instanceof DefaultDescriptor defaults) {
            return firstUnknownItem(defaults.getItemId(), translator);
        }
        // Item tags are resolved client-side and exist independently of the item component list.
        return null;
    }

    private static String firstUnknownItem(ItemData item, NeteaseItemTranslator translator) {
        return item == null ? null : firstUnknownItem(item.getDefinition(), translator);
    }

    private static String firstUnknownItem(ItemDefinition definition, NeteaseItemTranslator translator) {
        if (definition == null) {
            return null;
        }
        String identifier = definition.getIdentifier();
        if (translator.isKnown(definition)) {
            return null;
        }
        return identifier == null ? "<unknown>" : identifier;
    }

    /**
     * Recipe result stacks carry definitions and block ids straight from Geyser's 26.x palette.
     * Run them through the shared item translator so definitions and block ids match what the
     * inventory packets use. Recipe data is immutable, so a changed result rebuilds the recipe.
     */
    private static RecipeData translateResults(RecipeData recipe, NeteaseItemTranslator translator) {
        if (recipe instanceof CraftingRecipeData crafting) {
            List<ItemData> results = crafting.getResults();
            List<ItemData> translated = translateItems(results, translator);
            if (translated == results) {
                return recipe;
            }
            if (recipe instanceof ShapedRecipeData shaped) {
                return ShapedRecipeData.shaped(shaped.getId(), shaped.getWidth(), shaped.getHeight(),
                    shaped.getIngredients(), translated, shaped.getUuid(), shaped.getTag(),
                    shaped.getPriority(), shaped.getNetId(), shaped.isAssumeSymetry(), shaped.getRequirement());
            }
            if (recipe instanceof ShapelessRecipeData shapeless) {
                return ShapelessRecipeData.of(shapeless.getType(), shapeless.getId(), shapeless.getIngredients(),
                    translated, shapeless.getUuid(), shapeless.getTag(), shapeless.getPriority(),
                    shapeless.getNetId(), shapeless.getRequirement());
            }
            return recipe;
        }
        if (recipe instanceof SmithingTransformRecipeData smithing) {
            ItemData translated = translator.translateDefinitionOnly(smithing.getResult());
            if (translated == smithing.getResult()) {
                return recipe;
            }
            return SmithingTransformRecipeData.of(smithing.getId(), smithing.getTemplate(), smithing.getBase(),
                smithing.getAddition(), translated, smithing.getTag(), smithing.getNetId());
        }
        if (recipe instanceof FurnaceRecipeData furnace) {
            ItemData translated = translator.translateDefinitionOnly(furnace.getResult());
            if (translated == furnace.getResult()) {
                return recipe;
            }
            return FurnaceRecipeData.of(furnace.getType(), furnace.getInputId(), furnace.getInputData(),
                translated, furnace.getTag());
        }
        return recipe;
    }

    private static List<ItemData> translateItems(List<ItemData> results, NeteaseItemTranslator translator) {
        if (results == null) {
            return null;
        }
        List<ItemData> copy = null;
        for (int i = 0; i < results.size(); i++) {
            ItemData original = results.get(i);
            ItemData translated = translator.translateDefinitionOnly(original);
            if (translated != original) {
                if (copy == null) {
                    copy = new ArrayList<>(results);
                }
                copy.set(i, translated);
            }
        }
        return copy == null ? results : copy;
    }
}
