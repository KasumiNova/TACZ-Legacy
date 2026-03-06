package com.tacz.legacy.api.item

import com.tacz.legacy.api.item.attachment.AttachmentType
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * 配件 NBT 访问接口。
 * 当前先对齐工匠台与手持过滤所需的最小上游行为：配件 id 与配件类型。
 */
public interface IAttachment {
    public fun getAttachmentId(stack: ItemStack): ResourceLocation

    public fun setAttachmentId(stack: ItemStack, attachmentId: ResourceLocation?)

    public fun getType(stack: ItemStack): AttachmentType

    public companion object {
        @JvmStatic
        public fun getIAttachmentOrNull(stack: ItemStack?): IAttachment? = stack?.item as? IAttachment
    }
}