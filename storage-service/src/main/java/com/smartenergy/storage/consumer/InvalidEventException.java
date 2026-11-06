package com.smartenergy.storage.consumer;

/** Événement structurellement invalide : le rejouer n'y changera rien. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
