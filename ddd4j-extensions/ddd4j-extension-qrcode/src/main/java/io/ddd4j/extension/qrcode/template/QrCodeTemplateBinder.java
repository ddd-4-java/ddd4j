package io.ddd4j.extension.qrcode.template;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Binds simple {@code ${name}} placeholders in reusable frame text elements.
 * Image data remains behind {@code QrCodeResourceResolver}; this binder never performs I/O.
 */
public class QrCodeTemplateBinder {

    /**
     * Creates a new immutable frame and leaves the registered template unchanged.
     *
     * @param definition template definition
     * @param variables placeholder values
     * @return bound immutable frame
     */
    public QrCodeFrame bind(QrCodeTemplateDefinition definition, Map<String, ?> variables) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(variables, "variables must not be null");
        QrCodeFrame source = definition.getFrame();
        QrCodeFrame.Builder target = QrCodeFrame.builder(source.getWidth(), source.getHeight())
                .backgroundColor(source.getBackgroundColor());
        for (QrCodeFrameElement element : source.getElements()) {
            target.addElement(copy(element, variables));
        }
        return target.build();
    }

    /**
     * \u590d\u5236\u6a21\u677f\u5143\u7d20\u53ca\u5176\u5e03\u5c40\uff0c\u5e76\u628a\u6587\u672c\u5143\u7d20\u4e2d\u7684\u5360\u4f4d\u7b26\u66ff\u6362\u4e3a\u7ed9\u5b9a\u53d8\u91cf\u3002
     * @param element \u8981\u590d\u5236\u548c\u7ed1\u5b9a\u7684\u6a21\u677f\u5143\u7d20
     * @param variables \u4ece\u53d8\u91cf\u540d\u5230\u66ff\u6362\u503c\u7684\u6620\u5c04
     * @return \u7ed1\u5b9a\u540e\u7684\u6a21\u677f\u5143\u7d20
     */
    private QrCodeFrameElement copy(QrCodeFrameElement element, Map<String, ?> variables) {
        if (element instanceof QrCodeTextElement) {
            QrCodeTextElement text = (QrCodeTextElement) element;
            return QrCodeTextElement.builder(bindText(text.getText(), variables))
                    .bounds(text.getX(), text.getY(), text.getWidth(), text.getHeight())
                    .zIndex(text.getZIndex())
                    .font(text.getFontName(), text.getFontSize(), text.isBold())
                    .color(text.getColor())
                    .build();
        }
        if (element instanceof QrCodeImageElement) {
            QrCodeImageElement image = (QrCodeImageElement) element;
            return QrCodeImageElement.builder(image.getImage())
                    .bounds(image.getX(), image.getY(), image.getWidth(), image.getHeight())
                    .zIndex(image.getZIndex())
                    .build();
        }
        if (element instanceof QrCodeBlockElement) {
            QrCodeBlockElement block = (QrCodeBlockElement) element;
            return QrCodeBlockElement.builder()
                    .x(block.getX()).y(block.getY()).width(block.getWidth()).height(block.getHeight())
                    .zIndex(block.getZIndex())
                    .build();
        }
        return element;
    }

    /**
     * \u66ff\u6362\u6587\u672c\u4e2d\u5df2\u63d0\u4f9b\u53d8\u91cf\u5bf9\u5e94\u7684\u5360\u4f4d\u7b26\uff0c\u7a7a\u53d8\u91cf\u503c\u8f6c\u6210\u7a7a\u5b57\u7b26\u4e32\u3002
     * @param template \u5305\u542b\u53d8\u91cf\u5360\u4f4d\u7b26\u7684\u6587\u672c
     * @param variables \u7531\u8c03\u7528\u65b9\u63d0\u4f9b\u7684\u53d8\u91cf\u6620\u5c04
     * @return \u66ff\u6362\u5df2\u77e5\u53d8\u91cf\u540e\u7684\u6587\u672c
     */
    private String bindText(String template, Map<String, ?> variables) {
        String bound = template;
        for (Map.Entry<String, ?> variable : variables.entrySet()) {
            String placeholder = "${" + variable.getKey() + "}";
            bound = bound.replace(placeholder,
                    Objects.isNull(variable.getValue()) ? StringUtils.EMPTY : String.valueOf(variable.getValue()));
        }
        return bound;
    }
}
