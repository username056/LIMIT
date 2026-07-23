package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BatteryReportHtmlParserTests {

    private final BatteryReportHtmlParser parser = new BatteryReportHtmlParser();

    @Test
    void parsesAllFieldsAndComputesCapacityRatio() {
        DxdiagStyleHtml html =
                new DxdiagStyleHtml(
                        "SAMSUNG Electronics", "67,010 mWh", "55,584 mWh", "418");

        BatteryReportParseResult result = parser.parse(bytes(html.toXhtml()));

        assertThat(result.batteryManufacturer()).isEqualTo("SAMSUNG Electronics");
        assertThat(result.designCapacity()).isEqualTo("67,010 mWh");
        assertThat(result.fullChargeCapacity()).isEqualTo("55,584 mWh");
        assertThat(result.cycleCount()).isEqualTo(418);
        assertThat(result.capacityRatio()).isEqualByComparingTo("82.95");
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void returnsPartialResultWhenCycleCountIsMissing() {
        String xhtml =
                """
                <html><body><table>
                <tr><td><span class="label">MANUFACTURER</span></td><td>ACME</td></tr>
                <tr><td><span class="label">DESIGN CAPACITY</span></td><td>50,000 mWh</td></tr>
                <tr><td><span class="label">FULL CHARGE CAPACITY</span></td><td>40,000 mWh</td></tr>
                </table></body></html>
                """;

        BatteryReportParseResult result = parser.parse(bytes(xhtml));

        assertThat(result.cycleCount()).isNull();
        assertThat(result.capacityRatio()).isEqualByComparingTo("80.00");
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void returnsAllNullWhenExpectedLabelsAreAbsent() {
        String xhtml = "<html><body><p>no battery info here</p></body></html>";

        BatteryReportParseResult result = parser.parse(bytes(xhtml));

        assertThat(result.batteryManufacturer()).isNull();
        assertThat(result.designCapacity()).isNull();
        assertThat(result.capacityRatio()).isNull();
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void throwsWhenMarkupIsMalformed() {
        assertThatThrownBy(() -> parser.parse(bytes("<html><body><table><tr>")))
                .isInstanceOf(BatteryReportParseException.class);
    }

    @Test
    void allowsBareHtmlDoctypeButBlocksExternalEntities() {
        String xxePayload =
                """
                <?xml version="1.0"?>
                <!DOCTYPE html [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <html><body><table>
                <tr><td><span class="label">MANUFACTURER</span></td><td>&xxe;</td></tr>
                </table></body></html>
                """;

        BatteryReportParseResult result = parser.parse(bytes(xxePayload));

        assertThat(result.batteryManufacturer()).isNotEqualTo("file:///etc/passwd");
    }

    private byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private record DxdiagStyleHtml(
            String manufacturer, String designCapacity, String fullChargeCapacity, String cycleCount) {

        String toXhtml() {
            return """
                    <html><body><table>
                    <tr><td><span class="label">MANUFACTURER</span></td><td>%s</td></tr>
                    <tr><td><span class="label">DESIGN CAPACITY</span></td><td>%s</td></tr>
                    <tr><td><span class="label">FULL CHARGE CAPACITY</span></td><td>%s</td></tr>
                    <tr><td><span class="label">CYCLE COUNT</span></td><td>%s</td></tr>
                    </table></body></html>
                    """
                    .formatted(manufacturer, designCapacity, fullChargeCapacity, cycleCount);
        }
    }
}
