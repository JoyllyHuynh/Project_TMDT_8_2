import urllib.request

urls = [
    "http://localhost:8080/index.html",
    "http://localhost:8080/tin/html/home.html",
    "http://localhost:8080/tin/html/category.html",
    "http://localhost:8080/tin/html/category.html?genre=Comic%20Marvel%20%26%20DC",
    "http://localhost:8080/tin/html/category.html?q=one%20piece",
    "http://localhost:8080/tin/html/category.html?seller=AnVinh",
    "http://localhost:8080/tin/html/product_details.html",
    "http://localhost:8080/tin/html/shopping_cart.html",
    "http://localhost:8080/tin/html/payment.html",
    "http://localhost:8080/tin/html/voucher.html",
    "http://localhost:8080/tin/html/checkout.html",
    "http://localhost:8080/tin/html/cart.html",
    "http://localhost:8080/tin/html/voucher-vault.html",
    "http://localhost:8080/thuc/html/page1.html",
    "http://localhost:8080/thuc/html/page2.html",
    "http://localhost:8080/thuc/html/page3.html",
    "http://localhost:8080/thuc/html/page4.html",
    "http://localhost:8080/thuc/html/page5.html",
    "http://localhost:8080/thuc/html/page6.html",
    "http://localhost:8080/truong/html/index.html",
    "http://localhost:8080/truong/html/orders.html",
    "http://localhost:8080/truong/html/reviews.html",
    "http://localhost:8080/truong/html/wallet.html",
    "http://localhost:8080/truong/html/transactions.html",
    "http://localhost:8080/truong/html/withdraw.html",
    "http://localhost:8080/truong/html/revenue.html",
    "http://localhost:8080/tu/html/dashboard.html",
    "http://localhost:8080/duy/html/dealing-room.html",
    "http://localhost:8080/ttruongmap/index.html",
    "http://localhost:8080/ttruongmap/quan-ly-san-pham/index.html",
    "http://localhost:8080/ttruongmap/dang-ban-truyen/index.html",
    "http://localhost:8080/ttruongmap/an-xoa-bai-dang/index.html",
    "http://localhost:8080/ttruongmap/quan-ly-bai-dang/index.html",
    "http://localhost:8080/ttruongmap/quan-ly-voucher/index.html"
]

all_ok = True
print(f"Testing {len(urls)} project URLs...")
for u in urls:
    try:
        req = urllib.request.urlopen(u)
        code = req.getcode()
        if code == 200:
            print(f"  [200 OK] {u}")
        else:
            print(f"  [{code}] {u}")
            all_ok = False
    except Exception as e:
        print(f"  [FAIL] {u} -> {e}")
        all_ok = False

if all_ok:
    print("\n✅ ALL 34 URLs RETURNED 200 OK! ZERO 404 ERRORS!")
else:
    print("\n❌ SOME URLs FAILED!")
