/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossos;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class RayAttenuationComputeOutputTest {



    @Test
    public void testPropagationPathReceiverUnder() throws IOException {
        JsonMapper.Builder builder = JsonMapper.builder();
        JsonMapper mapper = builder.build();

        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));

        CnossosPath cnossosPath = mapper.readValue(
                RayAttenuationComputeOutputTest.class.getResourceAsStream("special_ray.json"),
                CnossosPath.class
        );
        AttenuationParameters attenuationCnossosParameters = new AttenuationParameters(false);
        double[] aBoundary = AttenuationCnossos.aBoundary(cnossosPath,attenuationCnossosParameters);
        for(double value : aBoundary) {
            assertFalse(Double.isNaN(value));
        }
    }
}
