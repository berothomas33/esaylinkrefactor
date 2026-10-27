package com.pax.configservice.xml;

import static org.junit.Assert.assertEquals;

import com.pax.bizentity.entity.EmvAid;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * A contact AID that CARDSCHEMECONFIGRATION doesn't map still gets terminal type (9F35) and
 * capabilities (9F33 / 9F40): from the ICS profile most mapped AIDs use.
 */
public class EmvXmlParamParserIcsTest {

    private static final String ICS_PROFILES = ""
            + "<ICSCONFIGURATION>"
            + "  <ICS><Type>MAIN</Type><TerminalType>22</TerminalType>"
            + "    <CardDataInputCapability>E0</CardDataInputCapability><CVMCapability>F0</CVMCapability>"
            + "    <SecurityCapability>C8</SecurityCapability>"
            + "    <AdditionalTerminalCapabilities>FF80F0A001</AdditionalTerminalCapabilities></ICS>"
            + "  <ICS><Type>OTHER</Type><TerminalType>21</TerminalType>"
            + "    <CardDataInputCapability>E0</CardDataInputCapability><CVMCapability>08</CVMCapability>"
            + "    <SecurityCapability>08</SecurityCapability>"
            + "    <AdditionalTerminalCapabilities>6000F0A001</AdditionalTerminalCapabilities></ICS>"
            + "</ICSCONFIGURATION>";

    @Test
    public void unmappedAidGetsTheMostUsedProfile() throws Exception {
        Map<String, EmvAid> aids = parse(ICS_PROFILES
                + "<CARDSCHEMECONFIGRATION>"
                + "  <CARDSCHEME><RID>A000000003</RID><ICSTYPE>MAIN</ICSTYPE></CARDSCHEME>"
                + "  <CARDSCHEME><RID>A000000004</RID><ICSTYPE>MAIN</ICSTYPE></CARDSCHEME>"
                + "  <CARDSCHEME><AID>A0000000043060</AID><ICSTYPE>OTHER</ICSTYPE></CARDSCHEME>"
                + "</CARDSCHEMECONFIGRATION>",
                "A0000000031010", "A0000000041010", "A0000000043060", "A000000732100123");

        EmvAid meeza = aids.get("A000000732100123");
        assertEquals("22", meeza.getTerminalType());
        assertEquals("E0F0C8", meeza.getTerminalCapability());
        assertEquals("FF80F0A001", meeza.getTerminalAdditionalCapability());
        // mapped AIDs keep their own profile
        assertEquals("21", aids.get("A0000000043060").getTerminalType());
        assertEquals("22", aids.get("A0000000031010").getTerminalType());
    }

    @Test
    public void withNoMappingAtAllTheFirstProfileIsUsed() throws Exception {
        Map<String, EmvAid> aids = parse(ICS_PROFILES, "A00000002501");

        EmvAid amex = aids.get("A00000002501");
        assertEquals("22", amex.getTerminalType());
        assertEquals("E0F0C8", amex.getTerminalCapability());
    }

    private static Map<String, EmvAid> parse(String config, String... aidIds) throws Exception {
        StringBuilder xml = new StringBuilder("<EMVPARAM>").append(config).append("<AIDLIST>");
        for (String aid : aidIds) {
            xml.append("<AID><ApplicationID>").append(aid).append("</ApplicationID></AID>");
        }
        xml.append("</AIDLIST></EMVPARAM>");
        List<EmvAid> parsed = EmvXmlParamParser.parse(
                new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8))).aids;
        Map<String, EmvAid> byAid = new HashMap<>();
        for (EmvAid aid : parsed) {
            byAid.put(aid.getAid(), aid);
        }
        return byAid;
    }
}
