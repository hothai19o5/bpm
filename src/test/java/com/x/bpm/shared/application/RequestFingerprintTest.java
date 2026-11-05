package com.x.bpm.shared.application;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RequestFingerprintTest {
    @Test void ignoresObjectFieldOrderButPreservesTypesArrayOrderAndValueBoundaries() {
        var a=new LinkedHashMap<String,Object>();a.put("values",Map.of("amount","9007199254740993","quantity",1));a.put("removeFields",List.of("x","y"));
        var b=new LinkedHashMap<String,Object>();b.put("removeFields",List.of("x","y"));b.put("values",Map.of("quantity",1,"amount","9007199254740993"));
        assertThat(RequestFingerprint.of(a)).isEqualTo(RequestFingerprint.of(b));
        assertThat(RequestFingerprint.of(1)).isNotEqualTo(RequestFingerprint.of(1.0)).isNotEqualTo(RequestFingerprint.of("1"));
        assertThat(RequestFingerprint.of(List.of("a","bc"))).isNotEqualTo(RequestFingerprint.of(List.of("ab","c")));
        assertThat(RequestFingerprint.of(List.of("x","y"))).isNotEqualTo(RequestFingerprint.of(List.of("y","x")));
        assertThatThrownBy(()->RequestFingerprint.of(Set.of("unordered"))).isInstanceOf(IllegalArgumentException.class);
    }
}
