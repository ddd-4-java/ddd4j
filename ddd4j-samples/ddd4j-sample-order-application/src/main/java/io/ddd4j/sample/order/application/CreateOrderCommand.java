package io.ddd4j.sample.order.application;public final class CreateOrderCommand {
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;

        public CreateOrderCommand(String orderNo, String buyerId, String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateOrderCommand other = (CreateOrderCommand) o;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderNo, buyerId, buyerName); }
        @Override
        public String toString() {
            return "CreateOrderCommand{" + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + "}";
        }
    }
