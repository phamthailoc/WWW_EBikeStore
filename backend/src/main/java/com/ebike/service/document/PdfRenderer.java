package com.ebike.service.document;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.slf4j.Slf4jLogger;
import com.openhtmltopdf.util.XRLog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * V27: fills a page template ({@code resources/documents/*.html}) and turns the page into a PDF.
 *
 * <p>The templates are well-formed XHTML (the PDF library reads them as XML). The font is DejaVu Sans, which has every Vietnamese letter and is
 * embedded in the file, so the text looks the same on any device. The bytes of the font are read once and shared by every render.
 */
@Component
public class PdfRenderer {

    private static final String FONT_FAMILY = "DejaVu Sans";
    private static final String FONT_REGULAR = "/net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";
    private static final String FONT_BOLD = "/net/sf/jasperreports/fonts/dejavu/DejaVuSans-Bold.ttf";

    static {
        // the PDF library logs through java.util.logging by default; send it to the application's own log instead
        XRLog.setLoggerImpl(new Slf4jLogger());
    }

    private final SpringTemplateEngine engine;
    private volatile byte[] regular;
    private volatile byte[] bold;

    public PdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("documents/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        this.engine = templateEngine;
    }

    /** The finished page as text, before it becomes a PDF (a test can read it without opening a PDF). */
    public String page(String template, Map<String, Object> model) {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariables(model);
        return engine.process(template, context);
    }

    /** The PDF of a template filled with {@code model}. */
    public byte[] render(String template, Map<String, Object> model) {
        String html = page(template, model);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> stream(fontBytes(false)), FONT_FAMILY, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> stream(fontBytes(true)), FONT_FAMILY, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not render the PDF of " + template, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not render the PDF of " + template, exception);
        }
    }

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private byte[] fontBytes(boolean boldFace) {
        byte[] cached = boldFace ? bold : regular;
        if (cached == null) {
            cached = read(boldFace ? FONT_BOLD : FONT_REGULAR);
            if (boldFace) {
                bold = cached;
            } else {
                regular = cached;
            }
        }
        return cached;
    }

    private static byte[] read(String resource) {
        try (InputStream in = PdfRenderer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Font not found on the classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
