package com.uhuyy.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// Sistem bahasa sendiri supaya ganti bahasa langsung berlaku tanpa restart.
// Urutan bahasa di tiap baris terjemahan: id, en, zh, es, pt, ja, ko, ru, hi.
// Terjemahan dibuat Claude (AI), bukan penerjemah profesional.
internal object Lang {
    val codes = listOf("id", "en", "zh", "es", "pt", "ja", "ko", "ru", "hi")
    val names = listOf(
        "Bahasa Indonesia", "English", "中文", "Español", "Português",
        "日本語", "한국어", "Русский", "हिन्दी"
    )

    // "sys" = ikut bahasa HP, selain itu salah satu kode di atas
    var pref by mutableStateOf("sys")

    fun load(ctx: Context) {
        val v = ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).getString("lang", "sys") ?: "sys"
        pref = if (v == "sys" || v in codes) v else "sys"
    }

    fun save(ctx: Context, v: String) {
        pref = v
        ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).edit().putString("lang", v).apply()
    }

    val code: String
        get() {
            val p = pref
            if (p != "sys") return p
            val l = Locale.getDefault().language
            val n = if (l == "in") "id" else l
            return if (n in codes) n else "en"
        }

    val locale: Locale get() = Locale(code)
}

internal fun t(k: String): String {
    val a = M[k] ?: return k
    val i = Lang.codes.indexOf(Lang.code)
    return a.getOrElse(i) { a[1] }
}

internal fun tf(k: String, vararg args: Any): String = String.format(Lang.locale, t(k), *args)

private val M: HashMap<String, Array<String>> = hashMapOf<String, Array<String>>().apply {
    fun d(k: String, vararg v: String) { put(k, arrayOf(*v)) }

    // ---- Pengaturan ----
    d("settings", "Pengaturan", "Settings", "设置", "Ajustes", "Configurações", "設定", "설정", "Настройки", "सेटिंग्स")
    d("theme", "Tema", "Theme", "主题", "Tema", "Tema", "テーマ", "테마", "Тема", "थीम")
    d("language", "Bahasa", "Language", "语言", "Idioma", "Idioma", "言語", "언어", "Язык", "भाषा")
    d("about", "Tentang", "About", "关于", "Acerca de", "Sobre", "このアプリについて", "정보", "О приложении", "जानकारी")
    d("close", "Tutup", "Close", "关闭", "Cerrar", "Fechar", "閉じる", "닫기", "Закрыть", "बंद करें")
    d("apply", "Terapkan", "Apply", "应用", "Aplicar", "Aplicar", "適用", "적용", "Применить", "लागू करें")
    d("cancel", "Batal", "Cancel", "取消", "Cancelar", "Cancelar", "キャンセル", "취소", "Отмена", "रद्द करें")
    d("pick_photo", "Pilih foto", "Choose photo", "选择照片", "Elegir foto", "Escolher foto", "写真を選ぶ", "사진 선택", "Выбрать фото", "फ़ोटो चुनें")
    d("remove", "Hapus", "Remove", "移除", "Quitar", "Remover", "削除", "삭제", "Удалить", "हटाएँ")
    d("bg_photo", "Foto latar dari galeri", "Background photo from your gallery", "来自相册的背景照片", "Foto de fondo de tu galería", "Foto de fundo da sua galeria", "ギャラリーの背景写真", "갤러리의 배경 사진", "Фоновое фото из галереи", "गैलरी से बैकग्राउंड फ़ोटो")
    d("bright_label", "Gelapin  ↔  Cerahin", "Darker  ↔  Brighter", "更暗  ↔  更亮", "Más oscuro  ↔  Más claro", "Mais escuro  ↔  Mais claro", "暗く  ↔  明るく", "어둡게  ↔  밝게", "Темнее  ↔  Светлее", "गहरा  ↔  हल्का")
    d("adjust_title", "Atur posisi foto", "Adjust photo position", "调整照片位置", "Ajustar posición de la foto", "Ajustar posição da foto", "写真の位置を調整", "사진 위치 조정", "Положение фото", "फ़ोटो की स्थिति सेट करें")
    d("adjust_hint", "Geser untuk pindah, cubit untuk zoom. Yang tampak di sini = yang jadi latar.", "Drag to move, pinch to zoom. What you see here is what becomes the background.", "拖动移动，双指缩放。此处所见即为背景。", "Arrastra para mover y pellizca para hacer zoom. Lo que ves aquí será el fondo.", "Arraste para mover, pince para dar zoom. O que você vê aqui vira o fundo.", "ドラッグで移動、ピンチでズーム。ここに見える範囲が背景になります。", "드래그로 이동, 핀치로 확대/축소. 여기에 보이는 그대로 배경이 됩니다.", "Перетаскивайте и масштабируйте щипком. Что видно здесь — то и станет фоном.", "खिसकाकर हिलाएँ, पिंच करके ज़ूम करें। यहाँ जो दिखता है वही बैकग्राउंड बनेगा।")
    d("photo_fail", "Foto gagal dibuka", "Couldn't open the photo", "无法打开照片", "No se pudo abrir la foto", "Não foi possível abrir a foto", "写真を開けませんでした", "사진을 열 수 없습니다", "Не удалось открыть фото", "फ़ोटो नहीं खुल सकी")
    d("follow_phone", "Ikut bahasa HP", "Follow phone language", "跟随手机语言", "Seguir el idioma del teléfono", "Seguir o idioma do celular", "端末の言語に合わせる", "휴대폰 언어 따르기", "Как в телефоне", "फ़ोन की भाषा के अनुसार")
    d("lang_note", "Terjemahan dibuat oleh Claude (AI), bukan penerjemah profesional. Kalau ada yang janggal, maaf ya.", "Translations were made by Claude (an AI), not by a professional translator. Sorry if anything sounds off.", "翻译由 Claude（AI）完成，并非专业译者。如有不通顺之处，敬请见谅。", "Las traducciones las hizo Claude (una IA), no un traductor profesional. Disculpa si algo suena raro.", "As traduções foram feitas pelo Claude (uma IA), não por um tradutor profissional. Desculpe se algo soar estranho.", "翻訳は Claude（AI）によるもので、プロの翻訳者によるものではありません。不自然な点があればご容赦ください。", "번역은 전문 번역가가 아닌 Claude(AI)가 했습니다. 어색한 부분이 있다면 양해 부탁드립니다.", "Перевод выполнил Claude (ИИ), а не профессиональный переводчик. Извините, если что-то звучит странно.", "अनुवाद Claude (AI) ने किया है, किसी पेशेवर अनुवादक ने नहीं। कुछ अटपटा लगे तो क्षमा करें।")

    // ---- Home ----
    d("loading", "Memuat video...", "Loading videos...", "正在加载视频…", "Cargando videos...", "Carregando vídeos...", "動画を読み込み中…", "동영상 불러오는 중…", "Загрузка видео…", "वीडियो लोड हो रहे हैं…")
    d("perm_text", "Aplikasi butuh izin baca video supaya bisa nampilin semua video di HP lu. Nggak ada yang dikirim ke internet.", "The app needs permission to read videos so it can show the videos on your phone. Nothing is sent to the internet.", "应用需要读取视频的权限，才能显示手机里的视频。不会向互联网发送任何数据。", "La app necesita permiso para leer videos y mostrar los de tu teléfono. No se envía nada a internet.", "O app precisa de permissão para ler vídeos e mostrar os do seu celular. Nada é enviado à internet.", "端末内の動画を表示するため、動画の読み取り許可が必要です。インターネットには何も送信されません。", "휴대폰의 동영상을 표시하려면 동영상 읽기 권한이 필요합니다. 인터넷으로 전송되는 데이터는 없습니다.", "Приложению нужно разрешение на чтение видео, чтобы показывать видео на телефоне. Ничего не отправляется в интернет.", "फ़ोन के वीडियो दिखाने के लिए ऐप को वीडियो पढ़ने की अनुमति चाहिए। इंटरनेट पर कुछ नहीं भेजा जाता।")
    d("allow", "Izinkan", "Allow", "允许", "Permitir", "Permitir", "許可", "허용", "Разрешить", "अनुमति दें")
    d("no_videos", "Belum ada video di HP ini.", "No videos on this phone yet.", "手机里还没有视频。", "Aún no hay videos en este teléfono.", "Ainda não há vídeos neste celular.", "この端末にはまだ動画がありません。", "이 휴대폰에는 아직 동영상이 없습니다.", "На этом телефоне пока нет видео.", "इस फ़ोन में अभी कोई वीडियो नहीं है।")
    d("all_videos", "Semua video", "All videos", "所有视频", "Todos los videos", "Todos os vídeos", "すべての動画", "모든 동영상", "Все видео", "सभी वीडियो")
    d("n_videos", "%d video", "%d videos", "%d 个视频", "%d videos", "%d vídeos", "%d 本の動画", "동영상 %d개", "Видео: %d", "%d वीडियो")
    d("search", "Cari video", "Search videos", "搜索视频", "Buscar videos", "Buscar vídeos", "動画を検索", "동영상 검색", "Поиск видео", "वीडियो खोजें")
    d("sort_new", "Terbaru", "Newest", "最新", "Recientes", "Recentes", "新しい順", "최신순", "Новые", "नए")
    d("sort_name", "Nama", "Name", "名称", "Nombre", "Nome", "名前", "이름", "Имя", "नाम")
    d("sort_size", "Ukuran", "Size", "大小", "Tamaño", "Tamanho", "サイズ", "크기", "Размер", "आकार")

    // ---- Tentang ----
    d("version", "Versi", "Version", "版本", "Versión", "Versão", "バージョン", "버전", "Версия", "संस्करण")
    d("built_on", "Tanggal dibuat", "Build date", "制作日期", "Fecha de creación", "Data de criação", "作成日", "제작일", "Дата создания", "निर्माण तिथि")
    d("creator", "Pembuat", "Creator", "作者", "Creador", "Criador", "作者", "제작자", "Автор", "निर्माता")
    d("about_tag", "Pemutar video offline. Tanpa internet, tanpa iklan.", "Offline video player. No internet, no ads.", "离线视频播放器。无网络，无广告。", "Reproductor de video sin conexión. Sin internet, sin anuncios.", "Reprodutor de vídeo offline. Sem internet, sem anúncios.", "オフライン動画プレーヤー。通信なし、広告なし。", "오프라인 동영상 플레이어. 인터넷도 광고도 없습니다.", "Офлайн-видеоплеер. Без интернета и рекламы.", "ऑफ़लाइन वीडियो प्लेयर। न इंटरनेट, न विज्ञापन।")
    d("honest_title", "Tentang Pembuat", "About the Creator", "关于作者", "Sobre el creador", "Sobre o criador", "作者について", "제작자 정보", "Об авторе", "निर्माता के बारे में")
    d("honest_text", "Aplikasi ini dirancang, diuji, dan di-build oleh Choing. Kode programnya ditulis dengan bantuan AI (Claude dari Anthropic).", "This app was designed, tested and built by Choing. The code was written with the help of AI (Claude by Anthropic).", "这个应用由 Choing 设计、测试和构建。代码是在 AI（Anthropic 的 Claude）的帮助下编写的。", "Esta app fue diseñada, probada y compilada por Choing. El código se escribió con ayuda de IA (Claude, de Anthropic).", "Este app foi projetado, testado e compilado por Choing. O código foi escrito com ajuda de IA (Claude, da Anthropic).", "このアプリは Choing が設計・テスト・ビルドしました。コードは AI（Anthropic の Claude）の助けを借りて書かれています。", "이 앱은 Choing이 설계, 테스트, 빌드했습니다. 코드는 AI(Anthropic의 Claude)의 도움으로 작성되었습니다.", "Приложение разработано, протестировано и собрано Choing. Код написан с помощью ИИ (Claude от Anthropic).", "इस ऐप को Choing ने डिज़ाइन, टेस्ट और बिल्ड किया है। कोड AI (Anthropic के Claude) की मदद से लिखा गया है।")
    d("offline_title", "Sepenuhnya offline", "Fully offline", "完全离线", "Totalmente sin conexión", "Totalmente offline", "完全オフライン", "완전 오프라인", "Полностью офлайн", "पूरी तरह ऑफ़लाइन")
    d("offline_text", "App ini nggak punya izin internet, jadi nggak ada data yang keluar dari HP lu.", "This app has no internet permission, so no data leaves your phone.", "本应用没有联网权限，不会有数据离开你的手机。", "Esta app no tiene permiso de internet, así que ningún dato sale de tu teléfono.", "Este app não tem permissão de internet, então nenhum dado sai do seu celular.", "このアプリにはインターネット権限がないため、データが端末の外に出ることはありません。", "이 앱에는 인터넷 권한이 없어 데이터가 휴대폰 밖으로 나가지 않습니다.", "У приложения нет доступа в интернет, поэтому данные не покидают телефон.", "इस ऐप के पास इंटरनेट की अनुमति नहीं है, इसलिए कोई डेटा फ़ोन से बाहर नहीं जाता।")

    // ---- Player ----
    d("fit_pas", "Pas", "Fit", "适应", "Ajustar", "Ajustar", "フィット", "맞춤", "Вписать", "फ़िट")
    d("fit_crop", "Crop ringan", "Light crop", "轻微裁剪", "Recorte ligero", "Corte leve", "軽くトリミング", "약간 자르기", "Лёгкая обрезка", "हल्का क्रॉप")
    d("fit_fill", "Isi layar", "Fill screen", "填满屏幕", "Llenar pantalla", "Preencher tela", "画面いっぱい", "화면 채우기", "Заполнить экран", "स्क्रीन भरें")
    d("fit_stretch", "Regang", "Stretch", "拉伸", "Estirar", "Esticar", "引き伸ばし", "늘리기", "Растянуть", "खींचें")
    d("rot_auto", "Putar: auto", "Rotate: auto", "旋转：自动", "Rotar: auto", "Girar: auto", "回転：自動", "회전: 자동", "Поворот: авто", "रोटेट: ऑटो")
    d("rot_land", "Putar: datar", "Rotate: landscape", "旋转：横屏", "Rotar: horizontal", "Girar: paisagem", "回転：横", "회전: 가로", "Поворот: альбомный", "रोटेट: लैंडस्केप")
    d("rot_port", "Putar: tegak", "Rotate: portrait", "旋转：竖屏", "Rotar: vertical", "Girar: retrato", "回転：縦", "회전: 세로", "Поворот: портретный", "रोटेट: पोर्ट्रेट")
    d("cant_play", "Video ini nggak bisa diputar", "This video can't be played", "无法播放此视频", "No se puede reproducir este video", "Não é possível reproduzir este vídeo", "この動画は再生できません", "이 동영상을 재생할 수 없습니다", "Не удаётся воспроизвести видео", "यह वीडियो नहीं चल सकता")
    d("sub_set", "Subtitle dipasang", "Subtitles added", "已添加字幕", "Subtítulos añadidos", "Legendas adicionadas", "字幕を追加しました", "자막을 추가했습니다", "Субтитры добавлены", "सबटाइटल जोड़े गए")
    d("unlocked", "Kunci dibuka", "Unlocked", "已解锁", "Desbloqueado", "Desbloqueado", "ロック解除", "잠금 해제됨", "Разблокировано", "अनलॉक हो गया")
    d("bright_hud", "Terang", "Brightness", "亮度", "Brillo", "Brilho", "明るさ", "밝기", "Яркость", "चमक")
    d("vol_hud", "Volume", "Volume", "音量", "Volumen", "Volume", "音量", "볼륨", "Громкость", "वॉल्यूम")
    d("zoom_hud", "Zoom", "Zoom", "缩放", "Zoom", "Zoom", "ズーム", "확대", "Масштаб", "ज़ूम")
    d("sec_unit", "dtk", "s", "秒", "s", "s", "秒", "초", "с", "से")
    d("info_title", "Info video", "Video info", "视频信息", "Info del video", "Info do vídeo", "動画情報", "동영상 정보", "О видео", "वीडियो जानकारी")
    d("i_name", "Nama file", "File name", "文件名", "Nombre", "Nome do arquivo", "ファイル名", "파일 이름", "Имя файла", "फ़ाइल का नाम")
    d("i_loc", "Lokasi", "Location", "位置", "Ubicación", "Local", "場所", "위치", "Расположение", "स्थान")
    d("i_dur", "Durasi", "Duration", "时长", "Duración", "Duração", "再生時間", "재생 시간", "Длительность", "अवधि")
    d("i_date", "Tanggal", "Date", "日期", "Fecha", "Data", "日付", "날짜", "Дата", "तारीख")
    d("i_res", "Resolusi", "Resolution", "分辨率", "Resolución", "Resolução", "解像度", "해상도", "Разрешение", "रेज़ोल्यूशन")
    d("i_vc", "Kodek video", "Video codec", "视频编码", "Códec de video", "Codec de vídeo", "動画コーデック", "비디오 코덱", "Видеокодек", "वीडियो कोडेक")
    d("i_ac", "Kodek audio", "Audio codec", "音频编码", "Códec de audio", "Codec de áudio", "音声コーデック", "오디오 코덱", "Аудиокодек", "ऑडियो कोडेक")
    d("i_fps", "Frame rate", "Frame rate", "帧率", "Fotogramas", "Taxa de quadros", "フレームレート", "프레임 레이트", "Частота кадров", "फ़्रेम रेट")
    d("i_br", "Bitrate", "Bitrate", "码率", "Tasa de bits", "Taxa de bits", "ビットレート", "비트레이트", "Битрейт", "बिटरेट")
}
