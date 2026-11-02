package bank.interfaces.http;

import bank.application.command.AccountAlreadyExistsException;
import bank.application.command.AccountNotFoundException;
import bank.application.eventstore.ConcurrencyException;
import bank.application.query.AccountViewNotFoundException;
import bank.application.query.ReadModelBehindException;
import bank.application.query.TransferViewNotFoundException;
import bank.application.query.projection.ProjectionNotFoundException;
import bank.application.transfer.TransferAlreadyExistsException;
import bank.application.transfer.TransferNotFoundException;
import bank.domain.shared.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Corpo da requisicao invalido");
    }

    @ExceptionHandler({AccountNotFoundException.class, AccountViewNotFoundException.class})
    public ProblemDetail accountNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler({TransferNotFoundException.class, TransferViewNotFoundException.class})
    public ProblemDetail transferNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(TransferAlreadyExistsException.class)
    public ProblemDetail transferAlreadyExists(TransferAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "TRANSFER_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(ProjectionNotFoundException.class)
    public ProblemDetail projectionNotFound(ProjectionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "PROJECTION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ProblemDetail accountAlreadyExists(AccountAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ProblemDetail concurrencyConflict(ConcurrencyException e) {
        return problem(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail businessRuleViolation(DomainException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION", e.getMessage());
    }

    @ExceptionHandler(ReadModelBehindException.class)
    public ResponseEntity<ProblemDetail> readModelBehind(ReadModelBehindException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "READ_MODEL_BEHIND", e.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        problem.setProperty("code", code);
        return problem;
    }
}
