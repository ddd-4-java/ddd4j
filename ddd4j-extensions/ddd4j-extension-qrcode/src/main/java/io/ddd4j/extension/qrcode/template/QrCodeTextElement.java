package io.ddd4j.extension.qrcode.template;

import lombok.Getter;

/**
 * 文本外框元素。
 */
@Getter
public final class QrCodeTextElement extends QrCodeFrameElement {

    /** \u6a21\u677f\u753b\u5e03\u4e0a\u663e\u793a\u7684\u6587\u672c\uff0c\u53ef\u5728\u6e32\u67d3\u524d\u7ed1\u5b9a\u53d8\u91cf\u3002 */
    private final String text;
    /** \u7528\u4e8e\u7ed8\u5236\u6587\u672c\u5143\u7d20\u7684\u5b57\u4f53\u540d\u79f0\u3002 */
    private final String fontName;
    /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u65f6\u4f7f\u7528\u7684\u5b57\u53f7\u3002 */
    private final int fontSize;
    /** \u662f\u5426\u4ee5\u7c97\u4f53\u7ed8\u5236\u6587\u672c\u5143\u7d20\u3002 */
    private final boolean bold;
    /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u6240\u7528\u7684\u989c\u8272\u3002 */
    private final String color;

    /**
     * \u4fdd\u5b58\u6a21\u677f\u6587\u672c\u5143\u7d20\u7684\u8fb9\u754c\u3001\u5c42\u7ea7\u3001\u6587\u672c\u4e0e\u5b57\u4f53\u6837\u5f0f\u3002
     * @param builder \u63d0\u4f9b\u6587\u672c\u548c\u7ed8\u5236\u53c2\u6570\u7684\u6784\u5efa\u5668
     */
    private QrCodeTextElement(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.zIndex);
        this.text = builder.text;
        this.fontName = builder.fontName;
        this.fontSize = builder.fontSize;
        this.bold = builder.bold;
        this.color = builder.color;
    }

    /**
     * \u521b\u5efa\u6587\u672c\u5916\u6846\u5143\u7d20\u6784\u5efa\u5668\u3002
     * @param text \u5f85\u7ed8\u5236\u7684\u6587\u672c
     * @return \u65b0\u7684\u6784\u5efa\u5668\u5b9e\u4f8b
     */
    public static Builder builder(String text) {
        return new Builder(text);
    }

    /**
     * {@link QrCodeTextElement} \u6784\u5efa\u5668\u3002
     *
     *  <p>\u8bbe\u7f6e\u6587\u672c\u8fb9\u754c\u3001\u5b57\u4f53\u4e0e\u989c\u8272\u540e\u6784\u5efa\u4e0d\u53ef\u53d8\u5143\u7d20\u3002
     */
    public static final class Builder {
        /** \u8981\u89e3\u7801\u6216\u7ed8\u5236\u7684\u4e8c\u7ef4\u7801\u6a21\u677f\u6587\u672c\u5185\u5bb9\u3002 */
        private final String text;
        /** \u5143\u7d20\u5728\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u6a2a\u5411\u50cf\u7d20\u5750\u6807\u3002 */
        private int x;
        /** \u5143\u7d20\u5728\u6a21\u677f\u753b\u5e03\u4e2d\u7684\u7eb5\u5411\u50cf\u7d20\u5750\u6807\u3002 */
        private int y;
        /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u5bbd\u5ea6\u3002 */
        private int width;
        /** \u5728\u7f16\u7801\u6216\u7ed8\u5236\u65f6\u4f7f\u7528\u7684\u50cf\u7d20\u9ad8\u5ea6\u3002 */
        private int height;
        /** \u91cd\u53e0\u5143\u7d20\u5728\u753b\u5e03\u4e2d\u7684\u53e0\u653e\u987a\u5e8f\u3002 */
        private int zIndex;
        /** \u7528\u4e8e\u7ed8\u5236\u6587\u672c\u5143\u7d20\u7684\u5b57\u4f53\u540d\u79f0\u3002 */
        private String fontName = "SansSerif";
        /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u65f6\u4f7f\u7528\u7684\u5b57\u53f7\u3002 */
        private int fontSize = 16;
        /** \u662f\u5426\u4ee5\u7c97\u4f53\u7ed8\u5236\u6587\u672c\u5143\u7d20\u3002 */
        private boolean bold;
        /** \u7ed8\u5236\u6587\u672c\u5143\u7d20\u6240\u7528\u7684\u989c\u8272\u3002 */
        private String color = "#000000";

        /**
         * \u7528\u6307\u5b9a\u6587\u672c\u521d\u59cb\u5316\u53ef\u8fdb\u4e00\u6b65\u914d\u7f6e\u4f4d\u7f6e\u548c\u5b57\u4f53\u7684\u6784\u5efa\u5668\u3002
         * @param text \u8981\u7ed8\u5236\u7684\u6587\u672c
         */
        private Builder(String text) {
            this.text = text;
        }

        /**
         * \u8bbe\u7f6e\u6587\u672c\u663e\u793a\u8fb9\u754c\u3002
         * @param x \u5de6\u4e0a\u89d2 X \u5750\u6807
         * @param y \u5de6\u4e0a\u89d2 Y \u5750\u6807
         * @param width \u663e\u793a\u5bbd\u5ea6\uff08\u50cf\u7d20\uff09
         * @param height \u663e\u793a\u9ad8\u5ea6\uff08\u50cf\u7d20\uff09
         * @return \u5f53\u524d\u6784\u5efa\u5668
         */
        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * \u8bbe\u7f6e\u7ed8\u5236\u5c42\u7ea7\uff08\u503c\u8d8a\u5927\u8d8a\u9760\u4e0a\uff09\u3002
         * @param zIndex \u5c42\u7ea7\u503c
         * @return \u5f53\u524d\u6784\u5efa\u5668
         */
        public Builder zIndex(int zIndex) {
            this.zIndex = zIndex;
            return this;
        }

        /**
         * \u8bbe\u7f6e\u5b57\u4f53\u540d\u79f0\u3001\u5b57\u53f7\u4e0e\u662f\u5426\u52a0\u7c97\u3002
         * @param fontName \u5b57\u4f53\u540d\u79f0\uff08\u5982 {@code SansSerif}\uff09
         * @param fontSize \u5b57\u53f7
         * @param bold \u662f\u5426\u52a0\u7c97
         * @return \u5f53\u524d\u6784\u5efa\u5668
         */
        public Builder font(String fontName, int fontSize, boolean bold) {
            this.fontName = fontName;
            this.fontSize = fontSize;
            this.bold = bold;
            return this;
        }

        /**
         * \u8bbe\u7f6e\u6587\u672c\u989c\u8272\uff08CSS \u989c\u8272\u503c\uff0c\u9ed8\u8ba4 {@code #000000}\uff09\u3002
         * @param color \u6587\u672c\u989c\u8272
         * @return \u5f53\u524d\u6784\u5efa\u5668
         */
        public Builder color(String color) {
            this.color = color;
            return this;
        }

        /**
         * \u6784\u5efa\u4e0d\u53ef\u53d8\u7684\u6587\u672c\u5916\u6846\u5143\u7d20\u3002
         * @return \u6784\u5efa\u5b8c\u6210\u7684\u5143\u7d20\u5b9e\u4f8b
         */
        public QrCodeTextElement build() {
            return new QrCodeTextElement(this);
        }
    }
}
