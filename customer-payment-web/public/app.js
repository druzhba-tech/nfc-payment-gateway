let currentOrder = {
  id: "ORD-" + Math.floor(100000 + Math.random() * 900000),
  amount: 50.00,
  currency: "TJS",
  merchantName: 'Магазин "Фурӯшгоҳ"',
  merchantAccount: "992900000001"
};

// Инициализация при загрузке страницы
document.addEventListener("DOMContentLoaded", async () => {
  const params = new URLSearchParams(window.location.search);
  const orderId = params.get("order");
  const directAmount = params.get("amount");
  const directMerchant = params.get("merchant");

  if (orderId) {
    try {
      const res = await fetch(`/api/orders/${orderId}`);
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.order) {
          currentOrder = data.order;
        }
      }
    } catch (e) {
      console.warn("Локальный режим (без сервера или оффлайн)", e);
    }
  }

  if (directAmount) currentOrder.amount = parseFloat(directAmount);
  if (directMerchant) currentOrder.merchantName = directMerchant;
  if (orderId) currentOrder.id = orderId;

  // Обновляем UI
  updateUI();
});

function updateUI() {
  document.getElementById("merchantName").textContent = currentOrder.merchantName;
  document.getElementById("orderNumber").textContent = "Заказ #" + currentOrder.id;
  const formatted = currentOrder.amount.toFixed(2) + " TJS";
  document.getElementById("amountValue").textContent = formatted;
  document.getElementById("successAmount").textContent = formatted;
}

// Запуск банковского приложения через Deep Link (App-to-App)
function payWithBank(bankKey) {
  const { amount, id, merchantAccount, merchantName } = currentOrder;
  let deepLink = "";
  let bankTitle = "";

  switch (bankKey) {
    case "alif":
      bankTitle = "Alif Mobi";
      // Официальная схема вызова Alif Mobi с предзаполненными параметрами
      deepLink = `alifmobi://pay?merchant_id=${encodeURIComponent(merchantAccount)}&amount=${amount}&currency=TJS&comment=${encodeURIComponent("Оплата заказа " + id)}`;
      break;

    case "dc":
      bankTitle = "DC Next (Dushanbe City)";
      // Схема вызова DC Next
      deepLink = `dcnext://payment?account=${encodeURIComponent(merchantAccount)}&amount=${amount}&desc=${encodeURIComponent("Заказ " + id)}`;
      break;

    case "eskhata":
      bankTitle = "Эсхата Онлайн";
      // Схема вызова Банка Эсхата
      deepLink = `eskhata://pay?account=${encodeURIComponent(merchantAccount)}&amount=${amount}&order=${id}`;
      break;

    case "km":
      bankTitle = "Корти Милли / QR";
      // Национальный QR стандарт НБТ
      deepLink = `kortimilli://qr?amount=${amount}&merchant=${encodeURIComponent(merchantAccount)}&name=${encodeURIComponent(merchantName)}`;
      break;
  }

  console.log(`[DeepLink] Запуск приложения ${bankTitle}:`, deepLink);

  // Пытаемся открыть нативное банковское приложение на телефоне клиента
  window.location.href = deepLink;

  // Показываем уведомление пользователю
  setTimeout(() => {
    // Если приложение не открылось (например, не установлено), даем подсказку
    console.log(`Если ${bankTitle} не установлено, откроется страница веб-оплаты банка.`);
  }, 1200);
}

// Симуляция успешной оплаты для демонстрации синхронизации с киоском
async function simulatePaymentSuccess(chosenBank = "Alif Mobi") {
  try {
    await fetch(`/api/orders/${currentOrder.id}/pay`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ bank: chosenBank })
    });
  } catch (e) {
    console.error("Ошибка отправки статуса оплаты:", e);
  }

  // Переключаем на экран успеха
  document.getElementById("paymentView").style.display = "none";
  document.getElementById("successView").style.display = "block";
  document.getElementById("successBankUsed").textContent = `через ${chosenBank}`;
}
