package br.com.shema.crm.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.shema.crm.extraction.application.DocumentReader.ReadingFailed;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageShrinkerTest {

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void keepsASmallImageAsItIs() throws IOException {
        byte[] small = png(800, 600);

        ImageShrinker.Image image = ImageShrinker.shrink(small, "image/png");

        assertThat(image.bytes()).isSameAs(small);
        assertThat(image.contentType()).isEqualTo("image/png");
    }

    @Test
    void reducesABigPhotoToTheLongEdgeTheModelUses() throws IOException {
        ImageShrinker.Image image = ImageShrinker.shrink(png(4000, 3000), "image/png");

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(image.bytes()));
        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(result.getWidth()).isEqualTo(ImageShrinker.MAX_EDGE);
        assertThat(result.getHeight()).isEqualTo(1932);
    }

    @Test
    void refusesWhatIsNotAnImage() {
        assertThatThrownBy(() -> ImageShrinker.shrink(new byte[] {1, 2, 3}, "image/png"))
                .isInstanceOf(ReadingFailed.class)
                .hasMessageContaining("Não deu para abrir a imagem");
    }
}
