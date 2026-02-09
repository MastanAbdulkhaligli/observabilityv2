package az.magusframework.components.lib.testapp.service;

public class PaymentService {
    public String pay() {
        return "OK";
    }

    public String failBusiness() {
        throw new IllegalArgumentException("invalid amount");
    }

    public String failTechnical() {
        throw new RuntimeException("db timeout");
    }
}
