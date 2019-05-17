package net.helix.hlx;

import java.util.Arrays;
import java.util.Optional;

public enum HxiEvent {
    CREATE_MODULE("ENTRY_CREATE"),
    MODIFY_MODULE("ENTRY_MODIFY"),
    DELETE_MODULE("ENTRY_DELETE"),
    OVERFLOW("OVERFLOW"),
    UNKNOWN("UNKNOWN");

    private String name;

    HxiEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static HxiEvent fromName(String name) {
        Optional<HxiEvent> hxiEvent = Arrays.stream(HxiEvent.values()).filter(event -> event.name.equals(name)).findFirst();
        return hxiEvent.orElse(UNKNOWN);
    }
}
