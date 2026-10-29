package com.miazheng.twitch;


import com.miazheng.twitch.favorite.DuplicateFavoriteException;
import com.miazheng.twitch.model.TwitchErrorResponse;
import com.miazheng.twitch.user.DuplicateUsernameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


// Error codes are stable strings for the client; exception class names are not exposed
@ControllerAdvice
public class GlobalControllerExceptionHandler {


    @ExceptionHandler(DuplicateFavoriteException.class)
    public final ResponseEntity<TwitchErrorResponse> handleDuplicateFavoriteException(DuplicateFavoriteException e) {
        return new ResponseEntity<>(
                new TwitchErrorResponse("This item is already in your favorites.", "duplicate_favorite", null),
                HttpStatus.BAD_REQUEST
        );
    }


    @ExceptionHandler(DuplicateUsernameException.class)
    public final ResponseEntity<TwitchErrorResponse> handleDuplicateUsernameException(DuplicateUsernameException e) {
        return new ResponseEntity<>(
                new TwitchErrorResponse("That username is taken.", "username_taken", null),
                HttpStatus.CONFLICT
        );
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public final ResponseEntity<TwitchErrorResponse> handleInvalidRequestBody(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "The request is invalid.";
        String field = fieldError != null ? fieldError.getField() : null;
        return new ResponseEntity<>(
                new TwitchErrorResponse(message, "invalid_request", field),
                HttpStatus.BAD_REQUEST
        );
    }
}
