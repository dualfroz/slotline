package com.slotline.common;

public class SlugTakenException extends RuntimeException {

    public SlugTakenException(String slug) {
        super("Organization slug '" + slug + "' is already taken");
    }
}
