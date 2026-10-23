package dev.lino.blocksregenerator.engine;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DelayExpressionTest {
    @Test
    void evaluatesDocumentedPopulationFormula() {
        double result = DelayExpression.evaluate("%min_delay% + (%max_delay% - %min_delay%) / (1 + %player_count%)",
                Map.of("min_delay", 5d, "max_delay", 25d, "player_count", 3d), new Random(1));
        assertEquals(10, result);
    }

    @Test
    void preservesArithmeticPrecedenceAndPowerAssociativity() {
        assertEquals(-4, eval("-2^2"));
        assertEquals(512, eval("2^3^2"));
        assertEquals(14, eval("2 + 3 * 4"));
        assertEquals(20, eval("(2 + 3) * 4"));
        assertEquals(0.001, eval("1e-3"));
    }

    @Test
    void randomRangeRemainsWithinBounds() {
        for (int index = 0; index < 100; index++) {
            double value = eval("rUni(2, 4)");
            assertTrue(value >= 2 && value <= 4);
        }
        assertEquals(3, eval("rUni(3,3)"));
    }

    @Test
    void rejectsNonFiniteUnknownAndDeepExpressions() {
        assertThrows(IllegalArgumentException.class, () -> eval("1/0"));
        assertThrows(IllegalArgumentException.class, () -> eval("unknown(1)"));
        assertThrows(IllegalArgumentException.class, () -> eval("%missing%"));
        assertThrows(IllegalArgumentException.class, () -> eval("(".repeat(60) + "1" + ")".repeat(60)));
        assertThrows(IllegalArgumentException.class, () -> eval("1) trailing"));
        assertThrows(IllegalArgumentException.class, () -> eval("rUni(5,1)"));
    }

    @Test
    void documentedFunctionsWorkWithoutExecutingScripts() {
        assertEquals(7, eval("max(ceil(2.1), abs(-7))"));
        assertEquals(4, eval("sqrt(16)"));
        assertEquals(5, eval("round(4.8)"));
        assertThrows(IllegalArgumentException.class, () -> eval("Runtime.exec(1)"));
    }

    private double eval(String expression) {
        return DelayExpression.evaluate(expression, Map.of(), new Random());
    }
}
