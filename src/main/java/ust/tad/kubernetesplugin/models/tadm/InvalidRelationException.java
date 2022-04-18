package ust.tad.kubernetesplugin.models.tadm;

public class InvalidRelationException extends Exception{
    public InvalidRelationException(String errorMessage) {
        super(errorMessage);
    }
}
