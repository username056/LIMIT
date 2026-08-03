package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class BatteryReportHtmlParserTests {

    private final BatteryReportHtmlParser parser = new BatteryReportHtmlParser();

    @Test
    void parsesRealPowercfgBatteryReportSample() throws IOException {
        BatteryReportParseResult result = parser.parse(readFixture("fixtures/inspection/battery-report-sample.html"));

        assertThat(result.batteryManufacturer()).isEqualTo("SAMSUNG Electronics");
        assertThat(result.designCapacity()).isEqualTo("73,829 mWh");
        assertThat(result.fullChargeCapacity()).isEqualTo("69,840 mWh");
        assertThat(result.cycleCount()).isEqualTo(288);
        assertThat(result.capacityRatio()).isEqualByComparingTo("94.60");
        assertThat(result.isComplete()).isTrue();
    }

    private byte[] readFixture(String classpathLocation) throws IOException {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    @Test
    void parsesAllFieldsAndComputesCapacityRatio() {
        DxdiagStyleHtml html =
                new DxdiagStyleHtml(
                        "SAMSUNG Electronics", "67,010 mWh", "55,584 mWh", "418");

        BatteryReportParseResult result = parser.parse(bytes(html.toHtml()));

        assertThat(result.batteryManufacturer()).isEqualTo("SAMSUNG Electronics");
        assertThat(result.designCapacity()).isEqualTo("67,010 mWh");
        assertThat(result.fullChargeCapacity()).isEqualTo("55,584 mWh");
        assertThat(result.cycleCount()).isEqualTo(418);
        assertThat(result.capacityRatio()).isEqualByComparingTo("82.95");
        assertThat(result.foundFieldCount()).isEqualTo(4);
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    void returnsPartialResultWhenCycleCountIsMissing() {
        String html =
                """
                <html><body><table>
                <tr><td><span class="label">MANUFACTURER</span></td><td>ACME</td></tr>
                <tr><td><span class="label">DESIGN CAPACITY</span></td><td>50,000 mWh</td></tr>
                <tr><td><span class="label">FULL CHARGE CAPACITY</span></td><td>40,000 mWh</td></tr>
                </table></body></html>
                """;

        BatteryReportParseResult result = parser.parse(bytes(html));

        assertThat(result.cycleCount()).isNull();
        assertThat(result.capacityRatio()).isEqualByComparingTo("80.00");
        assertThat(result.foundFieldCount()).isEqualTo(3);
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void capsCapacityRatioAtOneHundredWhenFullChargeExceedsDesignCapacity() {
        DxdiagStyleHtml html =
                new DxdiagStyleHtml(
                        "SAMSUNG Electronics", "75,000 mWh", "75,675 mWh", "1");

        BatteryReportParseResult result = parser.parse(bytes(html.toHtml()));

        assertThat(result.capacityRatio()).isEqualByComparingTo("100.00");
    }

    @Test
    void returnsAllNullWhenExpectedLabelsAreAbsent() {
        String html = "<html><body><p>no battery info here</p></body></html>";

        BatteryReportParseResult result = parser.parse(bytes(html));

        assertThat(result.batteryManufacturer()).isNull();
        assertThat(result.designCapacity()).isNull();
        assertThat(result.capacityRatio()).isNull();
        assertThat(result.foundFieldCount()).isZero();
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void tolerantlyParsesMalformedMarkupWithoutCrashing() {
        String html = "<html><body><table><tr>";

        BatteryReportParseResult result = parser.parse(bytes(html));

        assertThat(result.foundFieldCount()).isZero();
    }

    @Test
    void throwsWhenHtmlBytesAreNull() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(BatteryReportParseException.class);
    }

    @Test
    void doesNotExpandCustomInternalDtdEntities() {
        String xxePayload =
                """
                <!DOCTYPE html [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <html><body><table>
                <tr><td><span class="label">MANUFACTURER</span></td><td>&xxe;</td></tr>
                </table></body></html>
                """;

        BatteryReportParseResult result = parser.parse(bytes(xxePayload));

        assertThat(result.batteryManufacturer()).isNotEqualTo("file:///etc/passwd");
    }

    private byte[] bytes(String html) {
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private record DxdiagStyleHtml(
            String manufacturer, String designCapacity, String fullChargeCapacity, String cycleCount) {

        String toHtml() {
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
