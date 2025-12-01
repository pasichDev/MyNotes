import ImageTool from '@editorjs/image';

/**
 * ImageToolClickable
 * -------------------
 * Extends the standard ImageTool so that photos can be opened with a **long press**.
 * The logic does not break the original ImageTool, does not interfere with upload or render(),
 * it simply attaches an additional listener to the block wrapper.
 */
export default class ImageToolClickable extends ImageTool {

    render() {
        const wrapper = super.render();
        // Set long-press listener only once
        if (!wrapper.__longPressBound) {
            wrapper.__longPressBound = true;

            let pressTimer = null;
            let isLongPress = false;

            const startPress = () => {
                isLongPress = false;
                pressTimer = setTimeout(() => {
                    isLongPress = true;

                    const blockId = this.block.id;
                    if (window.Android?.onImageBlockClick) {
                        window.Android.onImageBlockClick(blockId);
                    }

                }, 450); // ← час утримання
            };

            const cancelPress = () => {
                clearTimeout(pressTimer);
            };

            wrapper.addEventListener('pointerdown', startPress);
            wrapper.addEventListener('pointerup', cancelPress);
            wrapper.addEventListener('pointerleave', cancelPress);
            wrapper.addEventListener('pointercancel', cancelPress);
        }

        return wrapper;
    }
}
