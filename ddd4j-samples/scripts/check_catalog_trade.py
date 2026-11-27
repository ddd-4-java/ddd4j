"""在已启动的样例上验证商品建档、交易状态、快照和租户隔离。"""
import argparse
import json
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def check(base, prefix="/api", cqrs=False, auth_headers=None, check_legacy_routes=False):
    tenant = "demo-" + uuid.uuid4().hex[:20]

    def request(method, path, expected, body=None, tenant_id=tenant, decode=True, idempotency_key=None):
        headers = {"X-Tenant-Id": tenant_id, "Content-Type": "application/json"}
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key
        headers.update(auth_headers or {})
        data = json.dumps(body).encode() if body is not None else None
        endpoint = path if path.startswith("/") else prefix + "/" + path
        req = Request(base.rstrip("/") + endpoint, data=data, headers=headers, method=method)
        try:
            response = urlopen(req, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            if response.status != expected:
                raise AssertionError(f"{method} {path}: expected {expected}, got {response.status} {raw[:300]!r}")
            return json.loads(raw) if decode else None

    goods = {"spuName": "Lunch", "skuCode": "LUNCH-1", "skuName": "Weekday", "salePriceCent": 1250, "shopId": 7}
    request("POST", "goods/LUNCH", 201, goods)
    assert request("GET", "goods/LUNCH", 200)["skus"][0]["skuCode"] == "LUNCH-1"
    order = {"shopId": 7, "items": [{"skuCode": "LUNCH-1", "quantity": 2}]}
    request("POST", "orders/T001", 201, order)
    if cqrs:
        request("GET", "orders/T001", 404)
        request("POST", "projections/orders/T001", 200)
    snapshot = request("GET", "orders/T001", 200)
    assert snapshot["grossAmountCent"] == 2500
    assert snapshot["items"][0]["productNameSnapshot"] == "Lunch"
    payment_key = uuid.uuid4().hex
    request("POST", "orders/T001/payment", 400, {"paidAmountCent": 2500})
    request("POST", "orders/T001/payment", 409, {"paidAmountCent": 2499}, idempotency_key=payment_key)
    payment = request("POST", "orders/T001/payment", 200, {"paidAmountCent": 2500}, idempotency_key=payment_key)
    assert payment["orderState"] == "CONFIRMED"
    assert payment["paidAmountCent"] == 2500
    assert request("POST", "orders/T001/payment", 200, {"paidAmountCent": 2500}, idempotency_key=payment_key) == payment
    request("POST", "orders/T001/payment", 409, {"paidAmountCent": 2499}, idempotency_key=payment_key)
    if cqrs:
        assert request("GET", "orders/T001", 200)["orderState"] == "AWAITING_PAYMENT"
        request("POST", "projections/orders/T001", 200)
    assert request("GET", "orders/T001", 200)["paidAmountCent"] == 2500
    assert request("POST", "orders/T001/fulfill", 200)["fulfillmentState"] == "FULFILLED"
    if cqrs:
        request("POST", "projections/orders/T001", 200)
    assert request("GET", "orders/T001", 200)["fulfillmentState"] == "FULFILLED"
    if not auth_headers:
        request("POST", "orders/T002", 201, order)
        assert request("POST", "orders/T002/cancel", 200)["orderState"] == "CANCELLED"
        if cqrs:
            request("POST", "projections/orders/T002", 200)
        assert request("GET", "orders/T002", 200)["orderState"] == "CANCELLED"
    request("POST", "orders/T001", 409, order)
    request("GET", "orders/T001", 404, tenant_id=tenant + "-b")
    if check_legacy_routes:
        former_order = {"orderNo": "LEGACY", "buyerId": "demo", "buyerName": "Legacy"}
        for path in ("/orders", "/api/orders"):
            request("POST", path, 404, former_order, decode=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    parser.add_argument("--mode", choices=("default", "cqrs", "auth"), default="default")
    parser.add_argument("--auth-header", help="认证样例使用的请求头名称，从 CATALOG_TEST_TOKEN 读取请求头值")
    parser.add_argument("--check-legacy-routes", action="store_true", help="检查两个旧订单创建地址均返回 404")
    args = parser.parse_args()
    auth_headers = {}
    if args.auth_header:
        import os
        auth_headers[args.auth_header] = os.environ["CATALOG_TEST_TOKEN"]
    check(args.base_url, "/api" if args.mode == "default" else "/orders/catalog",
          args.mode == "cqrs", auth_headers, args.check_legacy_routes)
    print("商品建档、订单状态、快照查询及租户隔离通过")
