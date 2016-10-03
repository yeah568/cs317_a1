public class Response {
    int code;
    String message;
    public Response(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String toString() {
    	return Integer.toString(code) + message;
    }
}