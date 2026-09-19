let currentOrder = {
  id: "ORD-" + Math.floor(100000 + Math.random() * 900000),
  amount: 50.00,
  currency: "TJS",
  merchantName: 'Терминал оплаты',
  merchantPhone: "992928826696"
};

// Инициализация при загрузке страницы
document.addEventListener("DOMContentLoaded", async () => {
  const params = new URLSearchParams(window.location.search);
  const orderId = params.get("order");
  const directAmount = params.get("amount");
  const directMerchant = params.get("merchant");

  if (directAmount) currentOrder.amount = parseFloat(directAmount) || 50.0;
  if (directMerchant) currentOrder.merchantName = directMerchant;
  if (orderId) currentOrder.id = orderId;

  // Если запущен локальный сервер, пробуем запросить детали заказа
  if (orderId && !window.location.hostname.includes("github.io")) {
    try {
      const res = await fetch(`/api/orders/${orderId}`);
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.order) {
          currentOrder.amount = data.order.amount;
          currentOrder.merchantName = data.order.merchantName;
        }
      }
    } catch (e) {
      console.log("GitHub Pages / Offline mode active");
    }
  }

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
  const { amount, id, merchantPhone, merchantName } = currentOrder;
  let deepLink = "";
  let webFallback = "";
  let bankTitle = "";

  const cleanPhone = merchantPhone.replace(/[^0-9]/g, ""); // 992928826696
  const shortPhone = cleanPhone.startsWith("992") ? cleanPhone.substring(3) : cleanPhone; // 928826696

  switch (bankKey) {
    case "alif":
      bankTitle = "Alif Mobi";
      // Deep Link в Alif Mobi для перевода/оплаты по номеру телефона
      deepLink = `alifmobi://transfer?phone=${cleanPhone}&amount=${amount}&currency=TJS&comment=${encodeURIComponent("Заказ " + id)}`;
      webFallback = `https://alif.mobi/pay?phone=${cleanPhone}&amount=${amount}`;
      break;

    case "dc":
      bankTitle = "DC Next (Dushanbe City)";
      // Deep Link в DC Next
      deepLink = `dcnext://transfer?receiver=${cleanPhone}&amount=${amount}&comment=${encodeURIComponent("Заказ " + id)}`;
      webFallback = `https://dc.tj/`;
      break;

    case "eskhata":
      bankTitle = "Эсхата Онлайн";
      // Deep Link в Эсхата Онлайн
      deepLink = `eskhata://transfer?phone=${cleanPhone}&amount=${amount}`;
      webFallback = `https://eskhata.com/`;
      break;

    case "km":
      bankTitle = "Корти Милли / QR";
      // Единый национальный QR НБТ
      deepLink = `kortimilli://qr?amount=${amount}&phone=${cleanPhone}&name=${encodeURIComponent(merchantName)}`;
      webFallback = `https://kortimilli.tj/`;
      break;
  }

  console.log(`[DeepLink] Открытие приложения ${bankTitle}:`, deepLink);

  // 1. Пробуем открыть установленное приложение банка
  window.location.href = deepLink;

  // 2. Если приложение не перехватило ссылку за 1.5 секунды, даем подсказку
  setTimeout(() => {
    console.log(`Если приложение не открылось, используйте: ${webFallback}`);
  }, 1500);
}

// Симуляция успешной оплаты для демонстрации
async function simulatePaymentSuccess(chosenBank = "Alif Mobi") {
  try {
    if (!window.location.hostname.includes("github.io")) {
      await fetch(`/api/orders/${currentOrder.id}/pay`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ bank: chosenBank })
      });
    }
  } catch (e) {
    console.error("Status update:", e);
  }

  document.getElementById("paymentView").style.display = "none";
  document.getElementById("successView").style.display = "block";
  document.getElementById("successBankUsed").textContent = `через ${chosenBank}`;
}
