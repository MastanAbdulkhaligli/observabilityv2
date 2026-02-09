package az.magusframework.components.lib.testapp.repository;

public class PaymentRepository {

    public String saveOk() {
        return "OK";
    }

    public String saveBusinessFail() {
        throw new IllegalArgumentException("business");
    }

    public String saveTechnicalFail() {
        throw new IllegalStateException("technical");
    }
}
