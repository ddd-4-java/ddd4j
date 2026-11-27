package io.ddd4j.extension.qrcode.template;

import lombok.Getter;

/**
 * 二维码外框元素的公共坐标属性。
 */
@Getter
public abstract class QrCodeFrameElement {

    /** \u5143\u7d20\u5728\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u6a2a\u5411\u50cf\u7d20\u5750\u6807\u3002 */
    private final int x;
    /** \u5143\u7d20\u5728\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u7eb5\u5411\u50cf\u7d20\u5750\u6807\u3002 */
    private final int y;
    /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u5bbd\u5ea6\u3002 */
    private final int width;
    /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u9ad8\u5ea6\u3002 */
    private final int height;
    /** \u91cd\u53e0\u5143\u7d20\u5728\u753b\u5e03\u4e2d\u7684\u53e0\u653e\u987a\u5e8f\u3002 */
    private final int zIndex;

    /**
     * \u521b\u5efa\u5916\u6846\u5143\u7d20\u7684\u5750\u6807\u53ca\u5c42\u7ea7\u5c5e\u6027\u3002
     * @param x \u6c34\u5e73\u5750\u6807
     * @param y \u5782\u76f4\u5750\u6807
     * @param width \u5bbd\u5ea6
     * @param height \u9ad8\u5ea6
     * @param zIndex \u5c42\u7ea7
     */
    protected QrCodeFrameElement(int x, int y, int width, int height, int zIndex) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.zIndex = zIndex;
    }
}
