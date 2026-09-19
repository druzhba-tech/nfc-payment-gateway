const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const path = require('path');
const os = require('os');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Хранилище заказов в памяти
const orders = new Map();

// Получение локального IP-адреса для подключения в одной сети Wi-Fi
function getLocalIp() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return 'localhost';
}

// 1. Создание нового заказа (вызывается терминалом продавца)
app.post('/api/orders', (req, res) => {
  const { amount, description, merchantName, merchantAccount } = req.body;
  const orderId = 'ORD-' + Math.floor(100000 + Math.random() * 900000);
  
  const order = {
    id: orderId,
    amount: Number(amount) || 10,
    currency: 'TJS',
    description: description || 'Оплата покупки',
    merchantName: merchantName || 'Магазин "Фурӯшгоҳ"',
    merchantAccount: merchantAccount || '992900000001', // Номер счета/кошелька продавца
    status: 'PENDING',
    createdAt: new Date().toISOString()
  };

  orders.set(orderId, order);

  const localIp = getLocalIp();
  const paymentUrl = `http://${localIp}:${PORT}/?order=${orderId}`;

  res.json({
    success: true,
    order,
    paymentUrl
  });
});

// 2. Получение данных заказа (вызывается телефоном покупателя при открытии)
app.get('/api/orders/:id', (req, res) => {
  const order = orders.get(req.params.id);
  if (!order) {
    return res.status(404).json({ success: false, message: 'Заказ не найден' });
  }
  res.json({ success: true, order });
});

// 3. Подтверждение оплаты (Webhook от банка или клик клиентом/продавцом)
app.post('/api/orders/:id/pay', (req, res) => {
  const order = orders.get(req.params.id);
  if (!order) {
    return res.status(404).json({ success: false, message: 'Заказ не найден' });
  }

  order.status = 'PAID';
  order.paidAt = new Date().toISOString();
  order.paymentMethod = req.body.bank || 'Alif Mobi / DC Next';

  // Оповещаем все подключенные терминалы продавца через WebSocket
  broadcast({
    event: 'PAYMENT_SUCCESS',
    orderId: order.id,
    amount: order.amount,
    currency: order.currency,
    paidAt: order.paidAt,
    paymentMethod: order.paymentMethod
  });

  res.json({ success: true, order });
});

// 4. Получить локальный IP сервера
app.get('/api/info', (req, res) => {
  res.json({
    ip: getLocalIp(),
    port: PORT,
    ordersCount: orders.size
  });
});

// WebSocket для мгновенной обратной связи с терминалом продавца
const clients = new Set();
wss.on('connection', (ws) => {
  clients.add(ws);
  ws.on('close', () => clients.delete(ws));
});

function broadcast(data) {
  const message = JSON.stringify(data);
  for (const client of clients) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message);
    }
  }
}

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
  const localIp = getLocalIp();
  console.log(`=======================================================`);
  console.log(`🚀 Сервер оплаты NFC запущен!`);
  console.log(`🌐 Локальный адрес: http://localhost:${PORT}`);
  console.log(`📱 Адрес для смартфонов в Wi-Fi: http://${localIp}:${PORT}`);
  console.log(`=======================================================`);
});
