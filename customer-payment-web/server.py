import http.server
import socketserver
import json
import os
import socket
import urllib.parse
from datetime import datetime

PORT = 3000
PUBLIC_DIR = os.path.join(os.path.dirname(__file__), 'public')

# In-memory orders store
orders = {}
event_listeners = []

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

class PaymentHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=PUBLIC_DIR, **kwargs)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path.startswith("/api/orders/"):
            order_id = path.replace("/api/orders/", "").strip()
            order = orders.get(order_id)
            if order:
                self.send_json(200, {"success": True, "order": order})
            else:
                self.send_json(404, {"success": False, "message": "Order not found"})
            return

        elif path == "/api/events":
            # SSE for live payment notification
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            event_listeners.append(self.wfile)
            try:
                # Keep connection alive
                while True:
                    pass
            except (ConnectionResetError, BrokenPipeError):
                if self.wfile in event_listeners:
                    event_listeners.remove(self.wfile)
            return

        elif path == "/api/info":
            self.send_json(200, {
                "ip": get_local_ip(),
                "port": PORT,
                "ordersCount": len(orders)
            })
            return

        return super().do_GET()

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        try:
            data = json.loads(body) if body else {}
        except Exception:
            data = {}

        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/api/orders":
            order_id = f"ORD-{datetime.now().strftime('%H%M%S')}"
            amount = float(data.get("amount", 50.0))
            order = {
                "id": order_id,
                "amount": amount,
                "currency": "TJS",
                "description": data.get("description", "Оплата заказа"),
                "merchantName": data.get("merchantName", 'Магазин "Фурӯшгоҳ"'),
                "merchantAccount": data.get("merchantAccount", "992900000001"),
                "status": "PENDING",
                "createdAt": datetime.now().isoformat()
            }
            orders[order_id] = order
            local_ip = get_local_ip()
            payment_url = f"http://{local_ip}:{PORT}/?order={order_id}"

            self.send_json(200, {
                "success": True,
                "order": order,
                "paymentUrl": payment_url
            })
            return

        elif path.startswith("/api/orders/") and path.endswith("/pay"):
            order_id = path.replace("/api/orders/", "").replace("/pay", "").strip()
            order = orders.get(order_id)
            if not order:
                self.send_json(404, {"success": False, "message": "Order not found"})
                return

            order["status"] = "PAID"
            order["paidAt"] = datetime.now().isoformat()
            order["paymentMethod"] = data.get("bank", "Alif Mobi")

            # Broadcast SSE event
            event_data = json.dumps({
                "event": "PAYMENT_SUCCESS",
                "orderId": order["id"],
                "amount": order["amount"],
                "currency": order["currency"],
                "paidAt": order["paidAt"],
                "bank": order["paymentMethod"]
            })
            for listener in list(event_listeners):
                try:
                    listener.write(f"data: {event_data}\n\n".encode('utf-8'))
                    listener.flush()
                except Exception:
                    if listener in event_listeners:
                        event_listeners.remove(listener)

            self.send_json(200, {"success": True, "order": order})
            return

        self.send_json(404, {"error": "Not found"})

    def send_json(self, status_code, data):
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))

if __name__ == '__main__':
    local_ip = get_local_ip()
    print("=" * 60)
    print("🚀 Сервер NFC-оплаты (Таджикистан: Alif, DC Next, Eskhata)")
    print(f"🌐 Локальный адрес: http://localhost:{PORT}")
    print(f"📱 Адрес для смартфонов в Wi-Fi: http://{local_ip}:{PORT}")
    print("=" * 60)
    with socketserver.ThreadingTCPServer(("0.0.0.0", PORT), PaymentHandler) as httpd:
        httpd.serve_forever()
