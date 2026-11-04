package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.application.DocumentReader.ReadingFailed;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Prepara a foto de um pedido para a API. O modelo trabalha com até {@value #MAX_EDGE} px no lado maior e reduz o
 * que passar disso; reduzir aqui antes economiza envio e tokens sem perder leitura. A API aceita até 10 MB por imagem
 * em base64 (cerca de 7,5 MB de arquivo): acima de {@value #MAX_BYTES} bytes, a imagem é regravada em JPEG.
 */
final class ImageShrinker {

    static final int MAX_EDGE = 2576;
    static final int MAX_BYTES = 7 * 1024 * 1024;
    private static final float JPEG_QUALITY = 0.88f;

    record Image(byte[] bytes, String contentType) {}

    private ImageShrinker() {}

    static Image shrink(byte[] content, String contentType) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            throw new ReadingFailed("Não deu para abrir a imagem. Envie a foto em JPEG ou PNG, ou o PDF do pedido.");
        }
        int longEdge = Math.max(image.getWidth(), image.getHeight());
        if (longEdge <= MAX_EDGE && content.length <= MAX_BYTES) {
            return new Image(content, contentType);
        }
        double scale = Math.min(1.0, (double) MAX_EDGE / longEdge);
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        byte[] jpeg = jpeg(resized);
        if (jpeg.length > MAX_BYTES) {
            throw new ReadingFailed("A imagem é grande demais para a IA. Envie uma foto menor ou o PDF do pedido.");
        }
        return new Image(jpeg, "image/jpeg");
    }

    private static byte[] jpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
