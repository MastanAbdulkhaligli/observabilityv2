package az.magusframework.components.lib.testapp.service;

import az.magusframework.components.lib.testapp.repository.PaymentRepository;

public class PaymentAppService {
    private final PaymentRepository repo = new PaymentRepository();

    public String payOk() {
        return repo.saveOk();
    }

    public String payBusinessFail() {
        return repo.saveBusinessFail();
    }

    public String payTechnicalFail() {
        return repo.saveTechnicalFail();
    }
}
