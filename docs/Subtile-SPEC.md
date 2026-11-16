# Subtile – Faz 1 Spec (v0.1)

Android ana ekran widget editörü + resmi widget koleksiyonu. Topluluk Faz 2.

## Teknik karar: widget nasıl çizilir
Android widget'ları RemoteViews ile çalışır ve serbest yerleşimi desteklemez.
Karar: widget şablonu uygulama içinde **Bitmap olarak çizilir**, widget sadece bu görseli gösterir.
- Editörde ne görüyorsan ana ekranda aynısı çıkar.
- Her widget için tek bir ImageView yeterli, tasarım özgürlüğü tamdır.
- Dokunma alanları şablondaki "action" alanlarıyla tanımlanır (uygulama aç, takvim aç vb.).
- Güncelleme: WorkManager + sistem saat/pil yayınları. Dakika altı güncelleme yok.

## Stack
- Kotlin, Jetpack Compose (editör UI)
- AppWidgetProvider + Canvas ile bitmap render
- Room (yerel kayıt), kotlinx.serialization (JSON şablon)
- minSdk 26

## Şablon formatı (JSON)
Topluluk paylaşımı da bu formatla yapılacak. Kod yok, sadece veri.

```json
{
  "schema": 1,
  "name": "Soft Clock",
  "size": { "w": 4, "h": 2 },
  "background": { "type": "gradient", "colors": ["#1B1F3B", "#6A4C93"], "angle": 135, "radius": 28 },
  "layers": [
    { "type": "text", "source": "time", "format": "HH:mm", "x": 0.08, "y": 0.2, "size": 56, "color": "#FFFFFF", "font": "inter-bold" },
    { "type": "text", "source": "date", "format": "EEEE, d MMMM", "x": 0.08, "y": 0.62, "size": 16, "color": "#C9C3E6", "font": "inter-medium" },
    { "type": "battery", "style": "ring", "x": 0.82, "y": 0.2, "size": 40, "color": "#FF9F1C" }
  ],
  "actions": [
    { "area": [0, 0, 1, 1], "do": "open_clock_app" }
  ]
}
```

Konumlar 0–1 arası oran (farklı ekran boyutlarında bozulmasın diye).

## Faz 1 katmanları (MVP)
text, shape (dikdörtgen/daire), gradient/solid arka plan, image (kullanıcı fotoğrafı), battery, analog/dijital saat, takvim günü.
Veri kaynakları: time, date, battery, next_event (takvim izni gerekir).

## Editör MVP
1. Boyut seç (2x2, 4x2, 4x4)
2. Katman ekle, sürükle, boyutlandır
3. Renk/font/yazı özellikleri paneli
4. Canlı önizleme
5. Kaydet ve ana ekrana ekle
6. Geri al / ileri al

## Resmi koleksiyon (lansman)
8 widget, 3 tema (örn. minimal koyu, pastel, neon). Tek dokunuşla "kullan" ve sonra editörde düzenle.

## Monetizasyon (Faz 1)
Ücretsiz: 3 widget, temel katmanlar. Premium: sınırsız widget, tüm koleksiyon, ek fontlar. Aylık/yıllık abonelik + tek seferlik.

## Adım sırası
1. Spec onayı (bu dosya)
2. Android projesi iskeleti + şablon veri modelleri
3. Bitmap renderer (şablon → görsel)
4. İlk çalışan widget (hardcode şablon, ana ekranda)
5. Editör UI
6. Koleksiyon + premium
7. Play Store hazırlığı
