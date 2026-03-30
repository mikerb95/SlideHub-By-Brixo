package com.brixo.slidehub.ui.exception;

/**
 * Se lanza cuando se intenta registrar un usuario con username o email ya
 * existente.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
