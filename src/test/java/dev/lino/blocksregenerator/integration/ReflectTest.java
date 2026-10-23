package dev.lino.blocksregenerator.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReflectTest {
    @Test
    void selectsCompatibleOverloadsAndBoxesPrimitiveParameters() throws ReflectiveOperationException {
        Fixture fixture = new Fixture();
        assertEquals("text", Reflect.call(fixture, "value", "text"));
        assertEquals(4, Reflect.call(fixture, "value", 4));
        assertEquals(true, Reflect.call(Fixture.class, "enabled", true));
        assertEquals("again", Reflect.call(fixture, "value", "again"));
    }

    @Test
    void preservesProviderFailureAndRejectsMissingMethods() {
        ReflectiveOperationException error = assertThrows(ReflectiveOperationException.class,
                () -> Reflect.call(new Fixture(), "failure"));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertThrows(NoSuchMethodException.class, () -> Reflect.call(new Fixture(), "absent"));
    }

    @Test
    void choosesMostSpecificOverloadForNullAndRuntimeSubtype() throws ReflectiveOperationException {
        assertEquals("string", Reflect.call(new Fixture(), "specific", (Object) null));
        assertEquals("string", Reflect.call(new Fixture(), "specific", "value"));
    }

    @Test
    void rejectsUnrelatedNullOverloadsInsteadOfChoosingArbitrarily() {
        assertThrows(NoSuchMethodException.class, () -> Reflect.call(new Fixture(), "ambiguous", (Object) null));
    }

    public static final class Fixture {
        public String value(String value) { return value; }
        public int value(int value) { return value; }
        public static boolean enabled(boolean value) { return value; }
        public void failure() { throw new IllegalStateException("Provider failed"); }
        public String specific(Object value) { return "object"; }
        public String specific(CharSequence value) { return "sequence"; }
        public String specific(String value) { return "string"; }
        public String ambiguous(String value) { return "string"; }
        public String ambiguous(Integer value) { return "integer"; }
    }
}
