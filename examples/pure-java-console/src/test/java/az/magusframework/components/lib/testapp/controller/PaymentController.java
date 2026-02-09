package az.magusframework.components.lib.testapp.controller;

import az.magusframework.components.lib.testapp.service.PaymentAppService;

public class PaymentController {
    private final PaymentAppService service = new PaymentAppService();

    public String payOk() {
        return service.payOk();
    }

    public String payBusinessFail() {
        return service.payBusinessFail();
    }

    public String payTechnicalFail() {
        return service.payTechnicalFail();
    }
}
