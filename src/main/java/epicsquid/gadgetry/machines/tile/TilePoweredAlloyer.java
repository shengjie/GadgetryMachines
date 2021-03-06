package epicsquid.gadgetry.machines.tile;

import epicsquid.gadgetry.core.block.BlockTEOnOffHoriz;
import epicsquid.gadgetry.core.inventory.predicates.PredicateAlloyMaterial;
import epicsquid.gadgetry.core.lib.inventory.predicates.PredicateEmpty;
import epicsquid.gadgetry.core.lib.tile.TileModular;
import epicsquid.gadgetry.core.lib.tile.module.FaceConfig.FaceIO;
import epicsquid.gadgetry.core.lib.tile.module.Module;
import epicsquid.gadgetry.core.lib.tile.module.ModuleEnergy;
import epicsquid.gadgetry.core.lib.tile.module.ModuleInventory;
import epicsquid.gadgetry.core.recipe.AlloyRecipe;
import epicsquid.gadgetry.core.recipe.RecipeBase;
import epicsquid.gadgetry.core.util.Util;
import epicsquid.gadgetry.machines.GadgetryMachinesContent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TilePoweredAlloyer extends TileModular implements ITickable {

  public static final String BATTERY = "battery";
  public static final String INVENTORY = "inventory";
  public float angle = -1;
  public int[] progress = { 0, 100 };

  public TilePoweredAlloyer() {
    addModule(new ModuleInventory(INVENTORY, this, 4, "powered_alloyer", new int[] { 0, 1, 2 }, new int[] { 3 }) {
      @Override
      public boolean canInsertToSlot(int slot) {
        return slot != 3;
      }

      @Override
      public boolean canExtractFromSlot(int slot) {
        return true;
      }
    }.setSlotPredicate(0, new PredicateAlloyMaterial(0)).setSlotPredicate(1, new PredicateAlloyMaterial(1)).setSlotPredicate(2, new PredicateAlloyMaterial(2))
        .setSlotPredicate(3, new PredicateEmpty()));
    addModule(new ModuleEnergy(BATTERY, this, 160000, 1600, 1600));
    config.setAllIO(FaceIO.IN);
  }

  @Override
  public void update() {
    if (angle == -1) {
      IBlockState state = world.getBlockState(getPos());
      angle = state.getValue(BlockTEOnOffHoriz.facing).getHorizontalAngle() + 180f;
    }
    IBlockState state = world.getBlockState(getPos());
    if (!world.isRemote) {
      ModuleEnergy energy = (ModuleEnergy) modules.get(BATTERY);
      if (energy.battery.getEnergyStored() >= 20) {
        IInventory inv = (IInventory) modules.get(INVENTORY);
        if (!inv.getStackInSlot(0).isEmpty()) {
          ItemStack recipeOutput = ItemStack.EMPTY;
          AlloyRecipe r = AlloyRecipe.findRecipe(new ItemStack[] { inv.getStackInSlot(0), inv.getStackInSlot(1), inv.getStackInSlot(2) });
          if (r != null) {
            recipeOutput = r.getOutput();
          }
          if (r != null && inv.getStackInSlot(0).getCount() >= RecipeBase.getCount(r.inputs.get(0)) && inv.getStackInSlot(1).getCount() >= RecipeBase
              .getCount(r.inputs.get(1)) && inv.getStackInSlot(2).getCount() >= RecipeBase.getCount(r.inputs.get(2)) && (inv.getStackInSlot(3).isEmpty()
              || RecipeBase.stackMatches(recipeOutput, inv.getStackInSlot(3))
              && inv.getStackInSlot(3).getCount() <= inv.getStackInSlot(3).getMaxStackSize() - recipeOutput.getCount())) {
            energy.battery.extractEnergy(20, false);
            if (!state.getValue(BlockTEOnOffHoriz.active) && progress[0] > 3) {
              world.setBlockState(getPos(), state.withProperty(BlockTEOnOffHoriz.active, true), 8);
              world.notifyBlockUpdate(getPos(), state, state.withProperty(BlockTEOnOffHoriz.active, true), 8);
            }
            progress[0]++;
            if (progress[0] >= progress[1]) {
              progress[0] = 0;
              inv.decrStackSize(0, RecipeBase.getCount(r.inputs.get(0)));
              inv.decrStackSize(1, RecipeBase.getCount(r.inputs.get(1)));
              inv.decrStackSize(2, RecipeBase.getCount(r.inputs.get(2)));
              AlloyRecipe r2 = AlloyRecipe.findRecipe(new ItemStack[] { inv.getStackInSlot(0), inv.getStackInSlot(1), inv.getStackInSlot(2) });
              if (r2 == null) {
                if (state.getValue(BlockTEOnOffHoriz.active)) {
                  world.setBlockState(getPos(), state.withProperty(BlockTEOnOffHoriz.active, false), 8);
                  world.notifyBlockUpdate(getPos(), state, state.withProperty(BlockTEOnOffHoriz.active, false), 8);
                }
              }
              if (inv.getStackInSlot(3).isEmpty()) {
                inv.setInventorySlotContents(3, recipeOutput);
              } else {
                inv.getStackInSlot(3).grow(recipeOutput.getCount());
              }
            }
            markDirty();
          } else if (progress[0] > 0) {
            progress[0] = 0;
            markDirty();
          }
        } else if (progress[0] > 0) {
          progress[0] = 0;
          markDirty();
        }
      }
    }
    if (progress[0] > 0 && Util.rand.nextInt(14) == 0) {
      spawnParticle(state);
    }
    for (Module m : modules.values()) {
      m.onUpdate(this);
    }
  }

  public void spawnParticle(IBlockState state) {
    EnumFacing enumfacing = (EnumFacing) state.getValue(BlockTEOnOffHoriz.facing);
    double d0 = (double) pos.getX() + 0.5;
    double d1 = (double) pos.getY() + Util.rand.nextDouble() * 6.0 / 16.0;
    double d2 = (double) pos.getZ() + 0.5;
    double d3 = 0.52;
    double d4 = Util.rand.nextDouble() * 0.6D - 0.3D;

    if (Util.rand.nextDouble() < 0.1D) {
      world.playSound((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS,
          1.0F, 1.0F, false);
    }

    switch (enumfacing) {
    case WEST:
      world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 - 0.52D, d1, d2 + d4, 0.0D, 0.0D, 0.0D);
      world.spawnParticle(EnumParticleTypes.FLAME, d0 - 0.52D, d1, d2 + d4, 0.0D, 0.0D, 0.0D);
      break;
    case EAST:
      world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + 0.52D, d1, d2 + d4, 0.0D, 0.0D, 0.0D);
      world.spawnParticle(EnumParticleTypes.FLAME, d0 + 0.52D, d1, d2 + d4, 0.0D, 0.0D, 0.0D);
      break;
    case NORTH:
      world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + d4, d1, d2 - 0.52D, 0.0D, 0.0D, 0.0D);
      world.spawnParticle(EnumParticleTypes.FLAME, d0 + d4, d1, d2 - 0.52D, 0.0D, 0.0D, 0.0D);
      break;
    case SOUTH:
      world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + d4, d1, d2 + 0.52D, 0.0D, 0.0D, 0.0D);
      world.spawnParticle(EnumParticleTypes.FLAME, d0 + d4, d1, d2 + 0.52D, 0.0D, 0.0D, 0.0D);
    }
  }

  @Override
  public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
    return newState.getBlock() != GadgetryMachinesContent.powered_alloyer;
  }

  @Override
  public NBTTagCompound writeToNBT(NBTTagCompound tag) {
    tag.setInteger("progress", progress[0]);
    tag.setInteger("maxProgress", progress[1]);
    return super.writeToNBT(tag);
  }

  @Override
  public void readFromNBT(NBTTagCompound tag) {
    super.readFromNBT(tag);
    progress[0] = tag.getInteger("progress");
    progress[1] = tag.getInteger("maxProgress");
  }
}
