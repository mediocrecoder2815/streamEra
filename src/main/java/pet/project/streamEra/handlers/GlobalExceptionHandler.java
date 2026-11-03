//package pet.project.streamEra.handler;
//
//
//import jakarta.persistence.EntityNotFoundException;
//import org.springframework.http.ProblemDetail;
//import org.springframework.web.bind.annotation.ControllerAdvice;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//
//@ControllerAdvice
//public class GlobalExceptionHandler {
//    private static int NOT_FOUND = 404;
//    private static int BAD_REQUEST = 400;
//
//
//    @ExceptionHandler(EntityNotFoundException.class)
//    public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
//        ProblemDetail problemDetail = ProblemDetail.forStatus(NOT_FOUND);
//        problemDetail.setDetail(ex.getMessage());
//        return problemDetail;
//    }
//
//
//}
