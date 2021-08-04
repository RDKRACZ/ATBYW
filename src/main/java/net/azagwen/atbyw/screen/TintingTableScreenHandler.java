package net.azagwen.atbyw.screen;

import net.azagwen.atbyw.block.AtbywBlocks;
import net.azagwen.atbyw.block.entity.TintingTableFuels;
import net.azagwen.atbyw.item.CanvasBlockItem;
import net.azagwen.atbyw.item.SimpleColoredItem;
import net.azagwen.atbyw.main.AtbywScreenHandlerType;
import net.azagwen.atbyw.main.AtbywStats;
import net.azagwen.atbyw.util.AtbywUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.function.Consumer;

public class TintingTableScreenHandler extends ScreenHandler {
    private final CraftingResultInventory output;
    protected final Inventory input;
    private final PropertyDelegate propertyDelegate;
    private final ScreenHandlerContext context;
    private final PlayerEntity player;
    private final Slot ingredientSlot;
    private final Slot redSlot;
    private final Slot greenSlot;
    private final Slot blueSlot;

    public TintingTableScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(5), new ArrayPropertyDelegate(5), ScreenHandlerContext.EMPTY);
    }

    public TintingTableScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, ScreenHandlerContext context) {
        super(AtbywScreenHandlerType.TINTING_TABLE_SCREEN_HANDLER, syncId);
        checkSize(inventory, 5);
        checkDataCount(propertyDelegate, 5);
        this.input = new SimpleInventory(1) {
            public void markDirty() {
                super.markDirty();
                TintingTableScreenHandler.this.onContentChanged(this);
            }
        };
        this.output = new CraftingResultInventory();
        this.propertyDelegate = propertyDelegate;
        this.context = context;
        this.player = playerInventory.player;
        this.ingredientSlot = this.addSlot(new IngredientSlot(this.input, 0, 142, 14));
        this.addSlot(new OutputSlot(this.output, 1, 142, 51));
        this.redSlot = this.addSlot(new DyeSlot(inventory, 2, 181, 84, TintingTableFuels.RED));
        this.greenSlot = this.addSlot(new DyeSlot(inventory, 3, 181, 111, TintingTableFuels.GREEN));
        this.blueSlot = this.addSlot(new DyeSlot(inventory, 4, 181, 138, TintingTableFuels.BLUE));
        this.addProperties(propertyDelegate);

        for(int y = 0; y < 3; ++y) {
            for(int x = 0; x < 9; ++x) {
                this.addSlot(new Slot(playerInventory, (x + (y * 9) + 9), (8 + (x * 18)), (84 + (y * 18))));
            }
        }

        for(int x = 0; x < 9; ++x) {
            this.addSlot(new Slot(playerInventory, x, (8 + (x * 18)), 142));
        }

    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(this.context, player, AtbywBlocks.TINTING_TABLE);
    }

    protected void onTakeOutput(PlayerEntity player, ItemStack stack) {
        this.input.setStack(0, ItemStack.EMPTY);
        if (stack.getItem() instanceof CanvasBlockItem) {
            player.increaseStat(AtbywStats.COLOR_CANVAS_BLOCK, 1);
        }
        var color = this.getColor();
        this.consumeDye(AtbywUtils.getRed(color), this.getRedAmount(), stack, this::setRedAmount);
        this.consumeDye(AtbywUtils.getGreen(color), this.getGreenAmount(), stack, this::setGreenAmount);
        this.consumeDye(AtbywUtils.getBlue(color), this.getBlueAmount(), stack, this::setBlueAmount);
    }

    private void consumeDye(int channel, int dyeGauge, ItemStack stack, Consumer<Integer> dyeGaugeSetter) {
        channel = (int) Math.floor((channel / 255.0F) * 4) * stack.getCount();
        dyeGaugeSetter.accept(dyeGauge - channel);
    }

    /**
     * The output's color is set in {@code net.azagwen.atbyw.main.AtbywNetworking}
     * @see net.azagwen.atbyw.main.AtbywNetworking
     */
    protected static void updateResult(TintingTableScreenHandler handler, World world, PlayerEntity player, Inventory inputInventory, CraftingResultInventory resultInventory) {
        if (!world.isClient){
            if (handler.getRedAmount() > 0 && handler.getGreenAmount() > 0 && handler.getBlueAmount() > 0){
                var inputStack = inputInventory.getStack(0);
                var serverPlayerEntity = (ServerPlayerEntity) player;
                var outputStack = ItemStack.EMPTY;
                if (!inputStack.isEmpty()) {
                    outputStack = inputStack.copy();
                }

                resultInventory.setStack(0, outputStack);
                resultInventory.markDirty();
                handler.setPreviousTrackedSlot(1, outputStack);
                serverPlayerEntity.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 1, outputStack));
            }
        }
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        this.context.run((world, blockPos) -> {
            updateResult(this, world, this.player, this.input, this.output);
        });
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        this.context.run((world, blockPos) -> {
            this.dropInventory(player, this.input);
        });
    }

    @Override
    public void setProperty(int id, int value) {
        super.setProperty(id, value);
        this.sendContentUpdates();
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        var copiedStack = ItemStack.EMPTY;
        var slot = this.slots.get(index);
        if (slot.hasStack()) {
            var slotStack = slot.getStack();
            copiedStack = slotStack.copy();
            if ((index < 0 || index > 1) && index != 2 && index != 3 && index != 4) {
                if (this.redSlot.canInsert(slotStack)) {
                    if (!this.insertItem(slotStack, 2, 3, false) || this.ingredientSlot.canInsert(slotStack) && !this.insertItem(slotStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.greenSlot.canInsert(slotStack)) {
                    if (!this.insertItem(slotStack, 3, 4, false) || this.ingredientSlot.canInsert(slotStack) && !this.insertItem(slotStack, 2, 3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.blueSlot.canInsert(slotStack)) {
                    if (!this.insertItem(slotStack, 4, 5, false) || this.ingredientSlot.canInsert(slotStack) && !this.insertItem(slotStack, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.ingredientSlot.canInsert(slotStack)) {
                    if (!this.insertItem(slotStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 5 && index < 32) {
                    if (!this.insertItem(slotStack, 32, 41, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 32 && index < 41) {
                    if (!this.insertItem(slotStack, 5, 32, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.insertItem(slotStack, 5, 41, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.insertItem(slotStack, 5, 41, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickTransfer(slotStack, copiedStack);
            }

            if (slotStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }

            if (slotStack.getCount() == copiedStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTakeItem(player, slotStack);
        }

        return copiedStack;
    }

    public static boolean isValidIngredient(ItemStack stack) {
        return stack.getItem() instanceof SimpleColoredItem || stack.getItem() instanceof DyeableItem;
    }

    private static class IngredientSlot extends Slot {

        public IngredientSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return isValidIngredient(stack);
        }

        @Override
        public int getMaxItemCount() {
            return 64;
        }
    }

    private class OutputSlot extends Slot {

        public OutputSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxItemCount() {
            return 64;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            TintingTableScreenHandler.this.onTakeOutput(player, stack);
        }
    }

    private static class DyeSlot extends Slot {
        public final TintingTableFuels dye;

        public DyeSlot(Inventory inventory, int index, int x, int y, TintingTableFuels dye) {
            super(inventory, index, x, y);
            this.dye = dye;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.isOf(dye.getItem());
        }

        @Override
        public int getMaxItemCount() {
            return 64;
        }
    }

    //Setters
    public void setRedAmount(int amount) {
        this.propertyDelegate.set(0, amount);
    }

    public void setGreenAmount(int amount) {
        this.propertyDelegate.set(1, amount);
    }

    public void setBlueAmount(int amount) {
        this.propertyDelegate.set(2, amount);
    }

    public void setMode(int mode) {
        this.propertyDelegate.set(3, mode);
    }

    public void setColor(int color) {
        this.propertyDelegate.set(4, color);
    }

    //Getters
    public int getRedAmount() {
        return this.propertyDelegate.get(0);
    }

    public int getGreenAmount() {
        return this.propertyDelegate.get(1);
    }

    public int getBlueAmount() {
        return this.propertyDelegate.get(2);
    }

    public int getMode() {
        return this.propertyDelegate.get(3);
    }

    public int getColor() {
        return this.propertyDelegate.get(4);
    }
}
