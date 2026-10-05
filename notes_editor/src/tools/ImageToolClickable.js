import ImageTool from '@editorjs/image';

/**
 * ImageToolClickable
 * -------------------
 * Extends the standard ImageTool so that photos can be opened with a **long press**, and so an
 * image never changes the height of the note once it appears.
 *
 * The stored image's width and height travel in the block data (file.width / file.height). With
 * them the block reserves exactly the space the picture will take before it has loaded: while it
 * uploads, while it loads on opening the note, and when the upload finishes. Without them the
 * block grew from nothing (or from a 200 px placeholder) to the picture's height, pushing the
 * text the user was editing up or down.
 */
export default class ImageToolClickable extends ImageTool {

    constructor(params) {
        super(params);
        // While a picked image uploads, its preview already tells its proportions: reserve them
        // straight away instead of showing a fixed-height placeholder that then resizes.
        const showPreloader = this.ui.showPreloader.bind(this.ui);
        this.ui.showPreloader = (src) => {
            showPreloader(src);
            this.reserveFromPreview(src);
        };
    }

    reserveFromPreview(src) {
        if (!src) return;
        const probe = new Image();
        probe.onload = () => {
            const wrapper = this.ui?.nodes?.wrapper;
            if (wrapper && !wrapper.classList.contains('image-tool--reserved')) {
                this.reserveSpace({ width: probe.naturalWidth, height: probe.naturalHeight });
            }
        };
        probe.src = src;
    }

    render() {
        const wrapper = super.render();
        this.reserveSpace(this.data?.file);
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

    onUpload(response) {
        if (response?.success && response.file) {
            this.reserveSpace(response.file);
        }
        super.onUpload(response);
    }

    /**
     * Gives the image box the picture's aspect ratio until the picture itself is shown.
     *
     * @param {{width?: number, height?: number}} file stored image data
     */
    reserveSpace(file) {
        const width = Number(file?.width);
        const height = Number(file?.height);
        const wrapper = this.ui?.nodes?.wrapper;
        if (!wrapper || !(width > 0) || !(height > 0)) return;
        wrapper.style.setProperty('--reserved-ratio', `${width} / ${height}`);
        wrapper.classList.add('image-tool--reserved');
    }
}
