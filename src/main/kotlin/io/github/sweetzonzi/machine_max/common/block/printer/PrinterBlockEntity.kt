package cn.solarmoon.spark_core.printer

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IBlockEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController
import cn.solarmoon.spark_core.animation.anim.play.layer.getMainLayer
import cn.solarmoon.spark_core.animation.model.ModelController
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import ru.nsk.kstatemachine.event.Event
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.statemachine.createStdLibStateMachine
import ru.nsk.kstatemachine.statemachine.onStateEntry
import ru.nsk.kstatemachine.statemachine.processEventBlocking
import ru.nsk.kstatemachine.transition.onTriggered

open class PrinterBlockEntity(pos: BlockPos, state: BlockState): BaseContainerBlockEntity(PrinterRegister.PRINTER_BE.get(), pos, state), IBlockEntityAnimatable<PrinterBlockEntity> {

    class StartWorkEvent(val recipe: PrinterRecipe): Event
    class FinishWorkEvent: Event
    class ResetEvent: Event

    companion object {
        const val SIZE = 10
    }

    override val animatable: PrinterBlockEntity get() = this
    override val animController: AnimController = AnimController(this)
    override val modelController: ModelController = ModelController(this)

    private var items = NonNullList.withSize(SIZE, ItemStack.EMPTY)

    var time = 0
        protected set(value) {
            field = value
            setChanged()
        }

    var result: ItemStack
        get() = getItem(SIZE - 1)
        set(value) { setItem(SIZE - 1, value) }

    var currentRecipe: PrinterRecipe? = null
        protected set

    var printState = "idle"
        private set(value) {
            field = value
            setChanged()
        }

    open val workAnim = AnimInstance.create(this, "work")!!.apply {
        selfDriving = true
    }

    val stateMachine = createStdLibStateMachine {
        val idle = initialState("idle")
        val working = state("working")
        val finished = state("finished")

        idle.apply {
            onEntry {
                currentRecipe = null
                workAnim.cancel()
                time = 0
            }

            transition<StartWorkEvent> {
                targetState = working
                onTriggered {
                    val recipe = it.event.recipe
                    currentRecipe = recipe
                    time = 0
                    workAnim.refresh()
                    animController.getMainLayer().setAnimation(workAnim)
                }
            }
        }

        working.apply {
            transition<FinishWorkEvent> {
                targetState = finished
                onTriggered {
                    clearContent()
                    result = currentRecipe!!.assemble(CraftingInput.of(3, 3, items), level!!.registryAccess())
                    currentRecipe = null
                }
            }
            transition<ResetEvent> {
                targetState = idle
            }
        }

        finished.apply {
            transition<ResetEvent> {
                targetState = idle
                guard = {
                    result.isEmpty
                }
            }
        }

        onStateEntry { state ,b ->
            SparkCore.LOGGER.info("进入状态：${state.name}")
        }
    }

    open fun tick(level: Level, pos: BlockPos, state: BlockState) {
        animController.physTick()
        animController.tick()
        currentRecipe?.let { workAnim.time = workAnim.maxLength * (time.toDouble() / it.printTime) }

        val craftingInput = CraftingInput.of(3, 3, items)
        val recipeOpt = level.recipeManager.getRecipeFor(PrinterRegister.PRINTER_RECIPE_TYPE.type.get(), craftingInput, level)

        when {
            recipeOpt.isPresent && result.isEmpty -> {
                val recipe = recipeOpt.get().value
                printState = stateMachine.processEventBlocking(StartWorkEvent(recipe)).name
                if (time >= recipe.printTime) {
                    printState = stateMachine.processEventBlocking(FinishWorkEvent()).name
                } else {
                    time++
                }
            }
            else -> {
                printState = stateMachine.processEventBlocking(ResetEvent()).name
            }
        }

    }

    override fun getMaxStackSize(): Int {
        return 1
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        return slot < SIZE - 1
    }

    override fun getDefaultName(): Component {
        return Component.translatable("container.machine_max.printer")
    }

    override fun getItems(): NonNullList<ItemStack?> {
        return items
    }

    override fun setItems(items: NonNullList<ItemStack?>) {
        this.items = items
    }

    override fun createMenu(
        containerId: Int,
        inventory: Inventory
    ): AbstractContainerMenu? {
        return null
    }

    override fun getContainerSize(): Int {
        return SIZE
    }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        return super.removeItem(slot, amount)
    }

    override fun setChanged() {
        super.setChanged()
        if (level != null && !level!!.isClientSide) {
            level!!.sendBlockUpdated(blockPos, blockState, blockState, 3)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun onDataPacket(
        net: Connection,
        pkt: ClientboundBlockEntityDataPacket,
        lookupProvider: HolderLookup.Provider
    ) {
        super.onDataPacket(net, pkt, lookupProvider)
        handleUpdateTag(pkt.tag, lookupProvider)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = super.getUpdateTag(registries)
        ContainerHelper.saveAllItems(tag, items, registries)
        tag.putString("state", printState)
        tag.putInt("time", time)
        return tag
    }

    override fun handleUpdateTag(tag: CompoundTag, lookupProvider: HolderLookup.Provider) {
        super.handleUpdateTag(tag, lookupProvider)
        items = NonNullList.withSize(containerSize, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(tag, items, lookupProvider)
        printState = tag.getString("state")
        time = tag.getInt("time")
    }

}