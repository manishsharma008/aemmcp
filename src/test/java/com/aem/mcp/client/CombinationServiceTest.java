package com.aem.mcp.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CombinationServiceTest {
    @Test
    void generatesAllCombinations() throws IOException {
        String json = """
        {
          \"properties\": {
            \"properties/columns/column/labelDistancelabelDistance\": \"20\",
            \"properties/columns/column/sectionssections\": \"2\",
            \"properties/columns/column/legendLayout/decimaldecimal\": \"true\",
            \"properties/columns/column/legendLayout/decimalPlacesdecimalPlaces\": [\"1\", \"2\", \"0\"]
          },
          \"asset\": {
            \"asset/columns/column/dataFiledataFile\": \"true\",
            \"asset/columns/column/overrideoverride\": \"true\",
            \"asset/columns/column/container/well/variantvariant\": [\"default\", \"target\", \"subdivided\"],
            \"asset/columns/column/container/well/valuevalue\": \"true\",
            \"asset/columns/column/container/well/targettarget\": \"true\",
            \"asset/columns/column/container/well/minmin\": \"true\",
            \"asset/columns/column/container/well/maxmax\": \"true\"
          },
          \"performance\": {
            \"performance/columns/column/deferDefinedDisableddeferDefinedDisabled\": \"true\"
          },
          \"style\": {
            \"style/columns/modemode\": [\"\", \"beacon-on-light\", \"beacon-on-lighter\", \"beacon-on-dark\", \"beacon-on-darker\"],
            \"style/columns/motionmotion\": [\"\", \"disabled\", \"enabled\"]
          }
        }
        """;
        CombinationService service = new CombinationService();
        List<Map<String, String>> combos = service.generateCombinations(json);
        assertEquals(72, combos.size());
    }
}
