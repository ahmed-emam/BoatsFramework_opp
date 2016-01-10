package aemam.boatsframework_opp;

/**
 * Created by aemam on 1/4/16.
 */
public class LogicException extends Exception {
    private String message = null;
    public LogicException(){
        super();
    }
    public LogicException(String message){
        super(message);
        this.message = message;
    }

    @Override
    public String toString() {
        return this.message;
    }
}
