package ust.tad.kubernetesplugin.models.tsdm;

public class InvalidAnnotationException extends Exception {
    public InvalidAnnotationException(String errorMessage) {
        super(errorMessage);
    }    
}
