package customer;

import com.intuit.karate.junit5.Karate;

class CustomerCrudTest {

    @Karate.Test
    Karate testCustomerCrudLiberty() {
        return Karate.run("customer-crud-liberty").relativeTo(getClass());
    }
}
