-- =============================================================================
-- NiJu · Contenido v2: los 12 contextos de la vida cotidiana
-- OPCIONAL. Ejecutar DESPUÉS de las migraciones 1–6, seed.sql y seed_home_transport.sql.
-- Se puede ejecutar más de una vez sin duplicar datos.
--
--   · 7 contextos nuevos: Escuela, Playa, Comercio, Salud, Tecnología, Trabajo, Viajes
--     (2 lecciones cada uno: A1 y A2; 6 palabras por lección = 84 palabras nuevas).
--   · Orden del catálogo según el plan pedagógico (de lo cercano a lo lejano).
--   · Imagen ilustrativa (emoji) para las palabras concretas, sin repetir emoji:
--     así el ejercicio "relacionar con imágenes" nunca es ambiguo.
--   · Un diálogo corto por palabra nueva (ejercicio "completar conversaciones").
--
-- Formato de seed_words idéntico al de seed.sql: tools/audio/generate_audio.py lo lee
-- para generar los audios locales (ver docs/AUDIO.md). Mientras no se generen, la app
-- pronuncia con la voz en inglés del teléfono (TextToSpeech de Android).
-- =============================================================================

begin;

insert into public.contexts (slug, name, description, icon, sort_order) values
  ('school',     'Escuela',    'Clases, útiles y vida universitaria.',            'school',     1),
  ('beach',      'Playa',      'Sol, mar y actividades en la costa.',             'beach',      2),
  ('shopping',   'Comercio',   'Comprar ropa, precios, pagos y devoluciones.',    'shopping',   6),
  ('health',     'Salud',      'El cuerpo, síntomas y la visita al médico.',      'health',     7),
  ('technology', 'Tecnología', 'Dispositivos, internet y aplicaciones.',          'technology', 9),
  ('work',       'Trabajo',    'La oficina y la búsqueda de empleo.',             'work',      10),
  ('travel',     'Viajes',     'Hoteles, turismo y planes de viaje.',             'travel',    11)
on conflict (slug) do nothing;

-- Orden del catálogo: Hogar, Escuela, Playa, Restaurante, Aeropuerto, Supermercado,
-- Comercio, Salud, Transporte, Tecnología, Trabajo, Viajes.
update public.contexts c set sort_order = v.ord, emoji = v.emoji
  from (values ('home', 0, '🏠'), ('school', 1, '🏫'), ('beach', 2, '🏖️'), ('restaurant', 3, '🍽️'),
               ('airport', 4, '✈️'), ('supermarket', 5, '🛒'), ('shopping', 6, '🛍️'), ('health', 7, '🏥'),
               ('transport', 8, '🚌'), ('technology', 9, '💻'), ('work', 10, '💼'), ('travel', 11, '🧳'))
       as v(slug, ord, emoji)
 where c.slug = v.slug;

insert into public.lessons (context_id, slug, title, description, level, sort_order)
select c.id, v.slug, v.title, v.description, v.level::public.cefr_level, v.sort_order
  from (values
    ('school',     'school_classroom',  'En el salón',             'Personas y útiles de clase.',               'A1', 1),
    ('school',     'school_campus',     'En la universidad',       'Biblioteca, exámenes y horarios.',          'A2', 2),
    ('beach',      'beach_day',         'Un día de sol',           'Lo que llevas y haces en la playa.',        'A1', 1),
    ('beach',      'beach_activities',  'Actividades en la costa', 'Botes, islas y seguridad en el mar.',       'A2', 2),
    ('shopping',   'shopping_clothes',  'Comprar ropa',            'Prendas, tallas y probarse ropa.',          'A1', 1),
    ('shopping',   'shopping_store',    'En la tienda',            'Precios, rebajas, pagos y reembolsos.',     'A2', 2),
    ('health',     'health_body',       'El cuerpo y los síntomas','Cómo decir qué te duele.',                  'A1', 1),
    ('health',     'health_doctor',     'En el médico',            'Citas, medicinas y la farmacia.',           'A2', 2),
    ('technology', 'tech_devices',      'Dispositivos',            'Teléfono, portátil y accesorios.',          'A1', 1),
    ('technology', 'tech_internet',     'En internet',             'Contraseñas, descargas y correo.',          'A2', 2),
    ('work',       'work_office',       'En la oficina',           'Personas y rutinas del trabajo.',           'A1', 1),
    ('work',       'work_career',       'Buscar empleo',           'Entrevistas, salario y habilidades.',       'A2', 2),
    ('travel',     'travel_hotel',      'En el hotel',             'Habitación, recepción y desayuno.',         'A1', 1),
    ('travel',     'travel_trip',       'Planear el viaje',        'Vacaciones, museos y dinero.',              'A2', 2)
  ) as v(ctx, slug, title, description, level, sort_order)
  join public.contexts c on c.slug = v.ctx
on conflict (slug) do nothing;

create temporary table seed_words (
  lesson text, pos smallint, english text, spanish text, ipa text,
  pos_tag text, level text, sentence_en text, sentence_es text
) on commit drop;

insert into seed_words values
  -- Escuela · En el salón
  ('school_classroom', 1, 'teacher',  'profesor',   '/ˈtiːtʃər/',  'noun', 'A1', 'Our teacher explains the lesson very well.', 'Nuestro profesor explica muy bien la lección.'),
  ('school_classroom', 2, 'student',  'estudiante', '/ˈstuːdənt/', 'noun', 'A1', 'Every student has a book.',                  'Cada estudiante tiene un libro.'),
  ('school_classroom', 3, 'notebook', 'cuaderno',   '/ˈnoʊtbʊk/',  'noun', 'A1', 'Write the new words in your notebook.',      'Escribe las palabras nuevas en tu cuaderno.'),
  ('school_classroom', 4, 'pencil',   'lápiz',      '/ˈpensəl/',   'noun', 'A1', 'Can I borrow your pencil?',                  '¿Me prestas tu lápiz?'),
  ('school_classroom', 5, 'backpack', 'mochila',    '/ˈbækpæk/',   'noun', 'A1', 'My books are in my backpack.',               'Mis libros están en mi mochila.'),
  ('school_classroom', 6, 'homework', 'tarea',      '/ˈhoʊmwɜːrk/','noun', 'A1', 'I do my homework after dinner.',             'Hago mi tarea después de la cena.'),
  -- Escuela · En la universidad
  ('school_campus', 1, 'library',   'biblioteca',         '/ˈlaɪbreri/',  'noun', 'A2', 'I study in the library every afternoon.',  'Estudio en la biblioteca todas las tardes.'),
  ('school_campus', 2, 'exam',      'examen',             '/ɪɡˈzæm/',     'noun', 'A2', 'The exam starts at nine.',                 'El examen empieza a las nueve.'),
  ('school_campus', 3, 'schedule',  'horario',            '/ˈskedʒuːl/',  'noun', 'A2', 'Check your class schedule online.',        'Revisa tu horario de clases en internet.'),
  ('school_campus', 4, 'classmate', 'compañero de clase', '/ˈklæsmeɪt/',  'noun', 'A2', 'My classmate helps me with English.',      'Mi compañero de clase me ayuda con el inglés.'),
  ('school_campus', 5, 'grade',     'nota',               '/ɡreɪd/',      'noun', 'A2', 'She got a good grade in history.',         'Ella sacó una buena nota en historia.'),
  ('school_campus', 6, 'learn',     'aprender',           '/lɜːrn/',      'verb', 'A2', 'I want to learn English this year.',       'Quiero aprender inglés este año.'),
  -- Playa · Un día de sol
  ('beach_day', 1, 'sand',      'arena',           '/sænd/',       'noun', 'A1', 'The sand is very hot today.',              'La arena está muy caliente hoy.'),
  ('beach_day', 2, 'wave',      'ola',             '/weɪv/',       'noun', 'A1', 'That wave is very big.',                   'Esa ola es muy grande.'),
  ('beach_day', 3, 'sun',       'sol',             '/sʌn/',        'noun', 'A1', 'The sun is shining at the beach.',         'El sol brilla en la playa.'),
  ('beach_day', 4, 'towel',     'toalla',          '/ˈtaʊəl/',     'noun', 'A1', 'Put your towel on the sand.',              'Pon tu toalla sobre la arena.'),
  ('beach_day', 5, 'sunscreen', 'protector solar', '/ˈsʌnskriːn/', 'noun', 'A1', 'Use sunscreen to protect your skin.',      'Usa protector solar para proteger tu piel.'),
  ('beach_day', 6, 'swim',      'nadar',           '/swɪm/',       'verb', 'A1', 'The children swim in the sea.',            'Los niños nadan en el mar.'),
  -- Playa · Actividades en la costa
  ('beach_activities', 1, 'shell',     'concha',     '/ʃel/',        'noun', 'A2', 'I found a pink shell on the beach.',     'Encontré una concha rosada en la playa.'),
  ('beach_activities', 2, 'umbrella',  'sombrilla',  '/ʌmˈbrelə/',   'noun', 'A2', 'We sit under a big umbrella.',           'Nos sentamos bajo una sombrilla grande.'),
  ('beach_activities', 3, 'lifeguard', 'salvavidas', '/ˈlaɪfɡɑːrd/', 'noun', 'A2', 'The lifeguard watches the swimmers.',    'El salvavidas vigila a los nadadores.'),
  ('beach_activities', 4, 'island',    'isla',       '/ˈaɪlənd/',    'noun', 'A2', 'We can take a boat to the island.',      'Podemos tomar un bote hasta la isla.'),
  ('beach_activities', 5, 'boat',      'bote',       '/boʊt/',       'noun', 'A2', 'The boat leaves at ten.',                'El bote sale a las diez.'),
  ('beach_activities', 6, 'shore',     'orilla',     '/ʃɔːr/',       'noun', 'A2', 'We walk along the shore at sunset.',     'Caminamos por la orilla al atardecer.'),
  -- Comercio · Comprar ropa
  ('shopping_clothes', 1, 'shirt',  'camisa',   '/ʃɜːrt/',    'noun',   'A1', 'I like your blue shirt.',                'Me gusta tu camisa azul.'),
  ('shopping_clothes', 2, 'shoes',  'zapatos',  '/ʃuːz/',     'noun',   'A1', 'These shoes are very comfortable.',      'Estos zapatos son muy cómodos.'),
  ('shopping_clothes', 3, 'size',   'talla',    '/saɪz/',     'noun',   'A1', 'Do you have this in a bigger size?',     '¿Tiene esto en una talla más grande?'),
  ('shopping_clothes', 4, 'dress',  'vestido',  '/dres/',     'noun',   'A1', 'She is wearing a red dress.',            'Ella lleva un vestido rojo.'),
  ('shopping_clothes', 5, 'jacket', 'chaqueta', '/ˈdʒækɪt/',  'noun',   'A1', 'Take a jacket, it is cold.',             'Lleva una chaqueta, hace frío.'),
  ('shopping_clothes', 6, 'try on', 'probarse', '/traɪ ɑːn/', 'phrase', 'A1', 'Can I try on these pants?',              '¿Me puedo probar estos pantalones?'),
  -- Comercio · En la tienda
  ('shopping_store', 1, 'price',        'precio',             '/praɪs/',         'noun', 'A2', 'What is the price of this bag?',          '¿Cuál es el precio de esta bolsa?'),
  ('shopping_store', 2, 'sale',         'rebaja',             '/seɪl/',          'noun', 'A2', 'Everything in this store is on sale.',    'Todo en esta tienda está en rebaja.'),
  ('shopping_store', 3, 'customer',     'cliente',            '/ˈkʌstəmər/',     'noun', 'A2', 'The customer wants to pay by card.',      'El cliente quiere pagar con tarjeta.'),
  ('shopping_store', 4, 'refund',       'reembolso',          '/ˈriːfʌnd/',      'noun', 'A2', 'I would like a refund, please.',          'Quisiera un reembolso, por favor.'),
  ('shopping_store', 5, 'fitting room', 'probador',           '/ˈfɪtɪŋ ruːm/',   'noun', 'A2', 'The fitting room is at the back.',        'El probador está al fondo.'),
  ('shopping_store', 6, 'credit card',  'tarjeta de crédito', '/ˈkredɪt kɑːrd/', 'noun', 'A2', 'Can I pay with a credit card?',           '¿Puedo pagar con tarjeta de crédito?'),
  -- Salud · El cuerpo y los síntomas
  ('health_body', 1, 'head',     'cabeza',          '/hed/',      'noun',      'A1', 'My head hurts a lot.',              'Me duele mucho la cabeza.'),
  ('health_body', 2, 'fever',    'fiebre',          '/ˈfiːvər/',  'noun',      'A1', 'The baby has a high fever.',        'El bebé tiene fiebre alta.'),
  ('health_body', 3, 'cough',    'tos',             '/kɔːf/',     'noun',      'A1', 'I have a bad cough.',               'Tengo mucha tos.'),
  ('health_body', 4, 'headache', 'dolor de cabeza', '/ˈhedeɪk/',  'noun',      'A1', 'Loud music gives me a headache.',   'La música fuerte me da dolor de cabeza.'),
  ('health_body', 5, 'sick',     'enfermo',         '/sɪk/',      'adjective', 'A1', 'I feel sick today.',                'Hoy me siento enfermo.'),
  ('health_body', 6, 'rest',     'descansar',       '/rest/',     'verb',      'A1', 'You need to rest for two days.',    'Necesitas descansar dos días.'),
  -- Salud · En el médico
  ('health_doctor', 1, 'doctor',       'médico',        '/ˈdɑːktər/',      'noun', 'A2', 'The doctor checks my temperature.',          'El médico me toma la temperatura.'),
  ('health_doctor', 2, 'nurse',        'enfermera',     '/nɜːrs/',         'noun', 'A2', 'The nurse gives me a glass of water.',       'La enfermera me da un vaso de agua.'),
  ('health_doctor', 3, 'medicine',     'medicina',      '/ˈmedɪsən/',      'noun', 'A2', 'Take this medicine after meals.',            'Toma esta medicina después de las comidas.'),
  ('health_doctor', 4, 'appointment',  'cita',          '/əˈpɔɪntmənt/',   'noun', 'A2', 'I have an appointment at four.',             'Tengo una cita a las cuatro.'),
  ('health_doctor', 5, 'pharmacy',     'farmacia',      '/ˈfɑːrməsi/',     'noun', 'A2', 'The pharmacy is open all night.',            'La farmacia está abierta toda la noche.'),
  ('health_doctor', 6, 'prescription', 'receta médica', '/prɪˈskrɪpʃən/',  'noun', 'A2', 'You need a prescription for this medicine.', 'Necesitas una receta médica para esta medicina.'),
  -- Tecnología · Dispositivos
  ('tech_devices', 1, 'phone',    'teléfono', '/foʊn/',       'noun', 'A1', 'My phone is on the table.',             'Mi teléfono está en la mesa.'),
  ('tech_devices', 2, 'screen',   'pantalla', '/skriːn/',     'noun', 'A1', 'The screen of my laptop is broken.',    'La pantalla de mi portátil está rota.'),
  ('tech_devices', 3, 'keyboard', 'teclado',  '/ˈkiːbɔːrd/',  'noun', 'A1', 'This keyboard is very quiet.',          'Este teclado es muy silencioso.'),
  ('tech_devices', 4, 'charger',  'cargador', '/ˈtʃɑːrdʒər/', 'noun', 'A1', 'I forgot my phone charger.',            'Olvidé el cargador de mi teléfono.'),
  ('tech_devices', 5, 'battery',  'batería',  '/ˈbætəri/',    'noun', 'A1', 'The battery is almost empty.',          'La batería está casi vacía.'),
  ('tech_devices', 6, 'laptop',   'portátil', '/ˈlæptɑːp/',   'noun', 'A1', 'I take my laptop to class.',            'Llevo mi portátil a clase.'),
  -- Tecnología · En internet
  ('tech_internet', 1, 'password', 'contraseña',         '/ˈpæswɜːrd/', 'noun', 'A2', 'Never share your password.',               'Nunca compartas tu contraseña.'),
  ('tech_internet', 2, 'download', 'descargar',          '/ˈdaʊnloʊd/', 'verb', 'A2', 'You can download the app for free.',       'Puedes descargar la aplicación gratis.'),
  ('tech_internet', 3, 'website',  'sitio web',          '/ˈwebsaɪt/',  'noun', 'A2', 'The university website has the schedule.', 'El sitio web de la universidad tiene el horario.'),
  ('tech_internet', 4, 'email',    'correo electrónico', '/ˈiːmeɪl/',   'noun', 'A2', 'I will send you an email.',                'Te enviaré un correo electrónico.'),
  ('tech_internet', 5, 'update',   'actualizar',         '/ˌʌpˈdeɪt/',  'verb', 'A2', 'You need to update the app.',              'Necesitas actualizar la aplicación.'),
  ('tech_internet', 6, 'file',     'archivo',            '/faɪl/',      'noun', 'A2', 'Save the file before you close it.',       'Guarda el archivo antes de cerrarlo.'),
  -- Trabajo · En la oficina
  ('work_office', 1, 'office',   'oficina',              '/ˈɔːfɪs/',      'noun', 'A1', 'Our office is on the fifth floor.',    'Nuestra oficina está en el quinto piso.'),
  ('work_office', 2, 'boss',     'jefe',                 '/bɔːs/',        'noun', 'A1', 'My boss is very kind.',                'Mi jefe es muy amable.'),
  ('work_office', 3, 'meeting',  'reunión',              '/ˈmiːtɪŋ/',     'noun', 'A1', 'We have a meeting at ten.',            'Tenemos una reunión a las diez.'),
  ('work_office', 4, 'desk',     'escritorio',           '/desk/',        'noun', 'A1', 'Your keys are on my desk.',            'Tus llaves están en mi escritorio.'),
  ('work_office', 5, 'coworker', 'compañero de trabajo', '/ˈkoʊwɜːrkər/', 'noun', 'A1', 'My coworker sits next to me.',         'Mi compañero de trabajo se sienta a mi lado.'),
  ('work_office', 6, 'break',    'descanso',             '/breɪk/',       'noun', 'A1', 'We take a break at noon.',             'Tomamos un descanso al mediodía.'),
  -- Trabajo · Buscar empleo
  ('work_career', 1, 'job',       'empleo',       '/dʒɑːb/',      'noun', 'A2', 'She has a new job in a bank.',               'Ella tiene un empleo nuevo en un banco.'),
  ('work_career', 2, 'interview', 'entrevista',   '/ˈɪntərvjuː/', 'noun', 'A2', 'My job interview is on Monday.',             'Mi entrevista de trabajo es el lunes.'),
  ('work_career', 3, 'salary',    'salario',      '/ˈsæləri/',    'noun', 'A2', 'The salary is paid every month.',            'El salario se paga cada mes.'),
  ('work_career', 4, 'resume',    'hoja de vida', '/ˈrezəmeɪ/',   'noun', 'A2', 'Send your resume by email.',                 'Envía tu hoja de vida por correo.'),
  ('work_career', 5, 'skill',     'habilidad',    '/skɪl/',       'noun', 'A2', 'English is an important skill.',             'El inglés es una habilidad importante.'),
  ('work_career', 6, 'hire',      'contratar',    '/haɪər/',      'verb', 'A2', 'The company wants to hire two engineers.',   'La empresa quiere contratar a dos ingenieros.'),
  -- Viajes · En el hotel
  ('travel_hotel', 1, 'room',      'habitación', '/ruːm/',       'noun', 'A1', 'Our room has a view of the sea.',           'Nuestra habitación tiene vista al mar.'),
  ('travel_hotel', 2, 'reception', 'recepción',  '/rɪˈsepʃən/',  'noun', 'A1', 'Leave your key at the reception.',          'Deja tu llave en la recepción.'),
  ('travel_hotel', 3, 'breakfast', 'desayuno',   '/ˈbrekfəst/',  'noun', 'A1', 'Breakfast is served from seven to ten.',    'El desayuno se sirve de siete a diez.'),
  ('travel_hotel', 4, 'tourist',   'turista',    '/ˈtʊrɪst/',    'noun', 'A1', 'The tourist takes a lot of photos.',        'El turista toma muchas fotos.'),
  ('travel_hotel', 5, 'suitcase',  'maleta',     '/ˈsuːtkeɪs/',  'noun', 'A1', 'I pack my suitcase the night before.',      'Hago mi maleta la noche anterior.'),
  ('travel_hotel', 6, 'guide',     'guía',       '/ɡaɪd/',       'noun', 'A1', 'Our guide speaks three languages.',         'Nuestro guía habla tres idiomas.'),
  -- Viajes · Planear el viaje
  ('travel_trip', 1, 'trip',     'viaje',            '/trɪp/',       'noun',   'A2', 'We are planning a trip to Cartagena.', 'Estamos planeando un viaje a Cartagena.'),
  ('travel_trip', 2, 'vacation', 'vacaciones',       '/veɪˈkeɪʃən/', 'noun',   'A2', 'I need a vacation.',                   'Necesito unas vacaciones.'),
  ('travel_trip', 3, 'souvenir', 'recuerdo',         '/ˌsuːvəˈnɪr/', 'noun',   'A2', 'I bought a souvenir for my mother.',   'Compré un recuerdo para mi mamá.'),
  ('travel_trip', 4, 'currency', 'moneda',           '/ˈkɜːrənsi/',  'noun',   'A2', 'The local currency is the peso.',      'La moneda local es el peso.'),
  ('travel_trip', 5, 'museum',   'museo',            '/mjuˈziːəm/',  'noun',   'A2', 'The museum is closed on Mondays.',     'El museo cierra los lunes.'),
  ('travel_trip', 6, 'abroad',   'en el extranjero', '/əˈbrɔːd/',    'adverb', 'A2', 'She wants to study abroad.',           'Ella quiere estudiar en el extranjero.');

insert into public.words (english, spanish, pronunciation, part_of_speech, level)
select english, spanish, ipa, pos_tag::public.part_of_speech, level::public.cefr_level
  from seed_words
on conflict on constraint words_english_pos_unique do nothing;

insert into public.word_examples (word_id, context_id, sentence_en, sentence_es)
select w.id, l.context_id, s.sentence_en, s.sentence_es
  from seed_words s
  join public.words w on w.english = s.english and w.part_of_speech = s.pos_tag::public.part_of_speech
  join public.lessons l on l.slug = s.lesson
 where not exists (select 1 from public.word_examples e
                    where e.word_id = w.id and e.sentence_en = s.sentence_en);

insert into public.lesson_words (lesson_id, word_id, position)
select l.id, w.id, s.pos
  from seed_words s
  join public.words w on w.english = s.english and w.part_of_speech = s.pos_tag::public.part_of_speech
  join public.lessons l on l.slug = s.lesson
on conflict do nothing;

-- ----------------------------------------------------------------------------
-- Imagen ilustrativa (emoji). Solo palabras concretas; cada emoji una sola vez.
-- Incluye las palabras de seed.sql y seed_home_transport.sql si están cargadas.
-- ----------------------------------------------------------------------------
update public.words w set emoji = v.emoji
  from (values
    -- v1: restaurante, supermercado, aeropuerto
    ('waiter', '🤵'), ('water', '💧'), ('bill', '🧾'), ('delicious', '😋'), ('spicy', '🌶️'), ('dessert', '🍰'),
    ('cart', '🛒'), ('bread', '🍞'), ('milk', '🥛'), ('bag', '🛍️'), ('expensive', '💰'), ('discount', '🏷️'),
    ('passport', '🛂'), ('flight', '✈️'), ('ticket', '🎫'), ('delay', '⏳'), ('seat', '💺'),
    ('arrival', '🛬'), ('departure', '🛫'),
    -- v1.1: hogar y transporte
    ('kitchen', '🍳'), ('bathroom', '🛁'), ('living room', '🛋️'), ('garden', '🌷'), ('bed', '🛏️'),
    ('chair', '🪑'), ('door', '🚪'), ('window', '🪟'), ('lamp', '💡'), ('key', '🔑'),
    ('bus', '🚌'), ('train', '🚆'), ('taxi', '🚕'), ('station', '🚉'), ('bike', '🚲'), ('map', '🗺️'),
    ('left', '⬅️'), ('right', '➡️'), ('straight', '⬆️'),
    -- v2
    ('teacher', '🧑‍🏫'), ('student', '🧑‍🎓'), ('notebook', '📓'), ('pencil', '✏️'), ('backpack', '🎒'),
    ('homework', '📝'), ('library', '📚'), ('schedule', '📅'),
    ('wave', '🌊'), ('sun', '☀️'), ('sunscreen', '🧴'), ('swim', '🏊'), ('shell', '🐚'), ('umbrella', '⛱️'),
    ('lifeguard', '🛟'), ('island', '🏝️'), ('boat', '⛵'),
    ('shirt', '👕'), ('shoes', '👟'), ('dress', '👗'), ('jacket', '🧥'), ('price', '💲'), ('credit card', '💳'),
    ('fever', '🤒'), ('headache', '🤕'), ('sick', '🤢'), ('rest', '😴'), ('doctor', '🧑‍⚕️'), ('medicine', '💊'),
    ('phone', '📱'), ('screen', '🖥️'), ('keyboard', '⌨️'), ('charger', '🔌'), ('battery', '🔋'), ('laptop', '💻'),
    ('password', '🔒'), ('download', '📥'), ('website', '🌐'), ('email', '📧'), ('update', '🔄'), ('file', '📁'),
    ('office', '🏢'), ('break', '☕'), ('salary', '💵'), ('hire', '🤝'),
    ('reception', '🛎️'), ('breakfast', '🥐'), ('tourist', '📸'), ('suitcase', '🧳'),
    ('vacation', '🌴'), ('souvenir', '🎁'), ('currency', '💱'), ('museum', '🏛️'), ('abroad', '🌍')
  ) as v(english, emoji)
 where w.english = v.english and w.emoji is distinct from v.emoji;

-- ----------------------------------------------------------------------------
-- Diálogos (completar conversaciones). La línea B contiene la palabra exacta.
-- ----------------------------------------------------------------------------
create temporary table seed_dialogues (
  english text, line_a_en text, line_a_es text, line_b_en text, line_b_es text
) on commit drop;

insert into seed_dialogues values
  ('teacher',   'Who is that woman?',                 '¿Quién es esa mujer?',                 'She is our English teacher.',              'Es nuestra profesora de inglés.'),
  ('student',   'Do you work here?',                  '¿Trabajas aquí?',                      'No, I am a student.',                      'No, soy estudiante.'),
  ('notebook',  'Where do you write your notes?',     '¿Dónde escribes tus apuntes?',         'I write them in my notebook.',             'Los escribo en mi cuaderno.'),
  ('pencil',    'I need something to write with.',    'Necesito algo para escribir.',         'Here, you can use my pencil.',             'Toma, puedes usar mi lápiz.'),
  ('backpack',  'Where are your books?',              '¿Dónde están tus libros?',             'They are in my backpack.',                 'Están en mi mochila.'),
  ('homework',  'Are you free tonight?',              '¿Estás libre esta noche?',             'No, I have a lot of homework.',            'No, tengo mucha tarea.'),
  ('library',   'Where can I study in silence?',      '¿Dónde puedo estudiar en silencio?',   'Go to the library on the first floor.',    'Ve a la biblioteca del primer piso.'),
  ('exam',      'Why are you so nervous?',            '¿Por qué estás tan nervioso?',         'I have a math exam tomorrow.',             'Mañana tengo un examen de matemáticas.'),
  ('schedule',  'When is our next class?',            '¿Cuándo es nuestra próxima clase?',    'Let me look at the schedule.',             'Déjame mirar el horario.'),
  ('classmate', 'Who is the boy next to you?',        '¿Quién es el chico a tu lado?',        'He is my classmate, Andrés.',              'Es mi compañero de clase, Andrés.'),
  ('grade',     'How was your test?',                 '¿Cómo te fue en la prueba?',           'Great, I got a high grade.',               'Muy bien, saqué una nota alta.'),
  ('learn',     'Why do you use this app?',           '¿Por qué usas esta app?',              'Because I want to learn new words.',       'Porque quiero aprender palabras nuevas.'),
  ('sand',      'Why are you wearing sandals?',       '¿Por qué llevas sandalias?',           'Because the sand is too hot.',             'Porque la arena está demasiado caliente.'),
  ('wave',      'Can we swim here?',                  '¿Podemos nadar aquí?',                 'Be careful, that wave is very strong.',    'Ten cuidado, esa ola es muy fuerte.'),
  ('sun',       'Do you want to sit here?',           '¿Quieres sentarte aquí?',              'No, I prefer to sit in the sun.',          'No, prefiero sentarme al sol.'),
  ('towel',     'I am all wet!',                      '¡Estoy todo mojado!',                  'Take my towel, it is dry.',                'Toma mi toalla, está seca.'),
  ('sunscreen', 'My face is red.',                    'Tengo la cara roja.',                  'You need more sunscreen.',                 'Necesitas más protector solar.'),
  ('swim',      'Do you want to go into the water?',  '¿Quieres entrar al agua?',             'Yes, I love to swim in the sea.',          'Sí, me encanta nadar en el mar.'),
  ('shell',     'What do you have in your hand?',     '¿Qué tienes en la mano?',              'A beautiful shell from the beach.',        'Una concha hermosa de la playa.'),
  ('umbrella',  'It is too sunny here.',              'Hace demasiado sol aquí.',             'Let''s sit under the umbrella.',           'Sentémonos bajo la sombrilla.'),
  ('lifeguard', 'Is it safe to swim here?',           '¿Es seguro nadar aquí?',               'Yes, there is a lifeguard on duty.',       'Sí, hay un salvavidas de turno.'),
  ('island',    'Where are you going on vacation?',   '¿A dónde vas de vacaciones?',          'To a small island in the Caribbean.',      'A una isla pequeña en el Caribe.'),
  ('boat',      'How do we get to the island?',       '¿Cómo llegamos a la isla?',            'We can go by boat.',                       'Podemos ir en bote.'),
  ('shore',     'Where are the kids?',                '¿Dónde están los niños?',              'They are playing near the shore.',         'Están jugando cerca de la orilla.'),
  ('shirt',     'What are you going to wear?',        '¿Qué te vas a poner?',                 'My white shirt and jeans.',                'Mi camisa blanca y unos jeans.'),
  ('shoes',     'Are you ready to go?',               '¿Estás listo para salir?',             'Wait, I need to put on my shoes.',         'Espera, tengo que ponerme los zapatos.'),
  ('size',      'Can I help you?',                    '¿Le puedo ayudar?',                    'Yes, I need a smaller size, please.',      'Sí, necesito una talla más pequeña, por favor.'),
  ('dress',     'What are you buying for the party?', '¿Qué vas a comprar para la fiesta?',   'A new dress.',                             'Un vestido nuevo.'),
  ('jacket',    'It is very cold outside.',           'Hace mucho frío afuera.',              'Then wear your jacket.',                   'Entonces ponte la chaqueta.'),
  ('try on',    'Do you like this shirt?',            '¿Te gusta esta camisa?',               'Yes, can I try on this one?',              'Sí, ¿me puedo probar esta?'),
  ('price',     'How much is this watch?',            '¿Cuánto cuesta este reloj?',           'The price is on the label.',               'El precio está en la etiqueta.'),
  ('sale',      'Why is this jacket so cheap?',       '¿Por qué esta chaqueta está tan barata?', 'Because it is on sale this week.',      'Porque está en rebaja esta semana.'),
  ('customer',  'Who is next?',                       '¿Quién sigue?',                        'That customer by the door.',               'Ese cliente junto a la puerta.'),
  ('refund',    'The shoes are too small.',           'Los zapatos me quedan pequeños.',      'You can get a refund with the receipt.',   'Puede obtener un reembolso con el recibo.'),
  ('fitting room', 'Where can I try on this dress?',  '¿Dónde me puedo probar este vestido?', 'The fitting room is over there.',          'El probador está por allá.'),
  ('credit card', 'Cash or card?',                    '¿Efectivo o tarjeta?',                 'Credit card, please.',                     'Tarjeta de crédito, por favor.'),
  ('head',      'What is wrong?',                     '¿Qué te pasa?',                        'I hit my head on the door.',               'Me golpeé la cabeza con la puerta.'),
  ('fever',     'You look tired.',                    'Te ves cansado.',                      'I think I have a fever.',                  'Creo que tengo fiebre.'),
  ('cough',     'Are you okay?',                      '¿Estás bien?',                         'Not really, I have a cough.',              'No mucho, tengo tos.'),
  ('headache',  'Why are you lying down?',            '¿Por qué estás acostado?',             'I have a terrible headache.',              'Tengo un dolor de cabeza terrible.'),
  ('sick',      'Why is Laura absent?',               '¿Por qué Laura no vino?',              'She is sick at home.',                     'Está enferma en casa.'),
  ('rest',      'What did the doctor say?',           '¿Qué dijo el médico?',                 'I need to rest and drink water.',          'Que necesito descansar y tomar agua.'),
  ('doctor',    'You are still sick.',                'Todavía estás enfermo.',               'Yes, I will call the doctor.',             'Sí, voy a llamar al médico.'),
  ('nurse',     'Who helps the doctor?',              '¿Quién ayuda al médico?',              'The nurse helps him every day.',           'La enfermera lo ayuda todos los días.'),
  ('medicine',  'How often do I take it?',            '¿Cada cuánto lo tomo?',                'Take the medicine every eight hours.',     'Toma la medicina cada ocho horas.'),
  ('appointment', 'Can you see the doctor now?',      '¿Puede ver al médico ahora?',          'No, I need an appointment first.',         'No, primero necesito una cita.'),
  ('pharmacy',  'Where can I buy this medicine?',     '¿Dónde puedo comprar esta medicina?',  'There is a pharmacy on the corner.',       'Hay una farmacia en la esquina.'),
  ('prescription', 'Can I buy these pills?',          '¿Puedo comprar estas pastillas?',      'Only with a prescription.',                'Solo con receta médica.'),
  ('phone',     'How can I call you?',                '¿Cómo te puedo llamar?',               'Here is my phone number.',                 'Aquí está mi número de teléfono.'),
  ('screen',    'I cannot see the picture.',          'No puedo ver la imagen.',              'Clean the screen first.',                  'Primero limpia la pantalla.'),
  ('keyboard',  'Why are you typing so slowly?',      '¿Por qué escribes tan lento?',         'This keyboard is new for me.',             'Este teclado es nuevo para mí.'),
  ('charger',   'My phone is dead.',                  'Mi teléfono se apagó.',                'You can use my charger.',                  'Puedes usar mi cargador.'),
  ('battery',   'Why is your phone off?',             '¿Por qué está apagado tu teléfono?',   'The battery is dead.',                     'Se acabó la batería.'),
  ('laptop',    'Do you have a computer?',            '¿Tienes computador?',                  'Yes, I have a small laptop.',              'Sí, tengo un portátil pequeño.'),
  ('password',  'I cannot open my account.',          'No puedo abrir mi cuenta.',            'Maybe you wrote the wrong password.',      'Quizás escribiste mal la contraseña.'),
  ('download',  'How do I get the file?',             '¿Cómo obtengo el archivo?',            'Click here to download it.',               'Haz clic aquí para descargarlo.'),
  ('website',   'Where can I see the prices?',        '¿Dónde puedo ver los precios?',        'On our website.',                          'En nuestro sitio web.'),
  ('email',     'How can I contact you?',             '¿Cómo te puedo contactar?',            'Send me an email, please.',                'Envíame un correo electrónico, por favor.'),
  ('update',    'The app is not working.',            'La aplicación no funciona.',           'Try to update it first.',                  'Intenta actualizarla primero.'),
  ('file',      'Did you get my homework?',           '¿Recibiste mi tarea?',                 'Yes, but the file is empty.',              'Sí, pero el archivo está vacío.'),
  ('office',    'Where do you work?',                 '¿Dónde trabajas?',                     'In an office downtown.',                   'En una oficina en el centro.'),
  ('boss',      'Can I leave early today?',           '¿Puedo salir temprano hoy?',           'You need to ask the boss.',                'Tienes que preguntarle al jefe.'),
  ('meeting',   'Are you busy?',                      '¿Estás ocupado?',                      'Yes, I am going to a meeting.',            'Sí, voy a una reunión.'),
  ('desk',      'Where is my notebook?',              '¿Dónde está mi cuaderno?',             'It is on your desk.',                      'Está en tu escritorio.'),
  ('coworker',  'Who is that man?',                   '¿Quién es ese señor?',                 'He is my new coworker.',                   'Es mi nuevo compañero de trabajo.'),
  ('break',     'I am so tired.',                     'Estoy muy cansado.',                   'Let''s take a short break.',               'Tomemos un descanso corto.'),
  ('job',       'Why are you so happy?',              '¿Por qué estás tan feliz?',            'I got the job!',                           '¡Conseguí el empleo!'),
  ('interview', 'Why are you wearing a suit?',        '¿Por qué llevas traje?',               'I have an interview today.',               'Hoy tengo una entrevista.'),
  ('salary',    'Do you like your new job?',          '¿Te gusta tu nuevo empleo?',           'Yes, and the salary is good.',             'Sí, y el salario es bueno.'),
  ('resume',    'What do you need for the job?',      '¿Qué necesitas para el empleo?',       'They asked for my resume.',                'Me pidieron la hoja de vida.'),
  ('skill',     'Why do you study English?',          '¿Por qué estudias inglés?',            'It is a useful skill for work.',           'Es una habilidad útil para el trabajo.'),
  ('hire',      'Is the store looking for people?',   '¿La tienda está buscando gente?',      'Yes, they want to hire a cashier.',        'Sí, quieren contratar a un cajero.'),
  ('room',      'Can I help you?',                    '¿Le puedo ayudar?',                    'Yes, I booked a room for two nights.',     'Sí, reservé una habitación por dos noches.'),
  ('reception', 'Where can I get a map?',             '¿Dónde consigo un mapa?',              'Ask at the reception.',                    'Pregunta en la recepción.'),
  ('breakfast', 'What time can we eat?',              '¿A qué hora podemos comer?',           'Breakfast starts at seven.',               'El desayuno empieza a las siete.'),
  ('tourist',   'Are you from here?',                 '¿Eres de aquí?',                       'No, I am a tourist.',                      'No, soy turista.'),
  ('suitcase',  'Are you ready for the trip?',        '¿Estás listo para el viaje?',          'Almost, I need to close my suitcase.',     'Casi, tengo que cerrar mi maleta.'),
  ('guide',     'Who knows the city?',                '¿Quién conoce la ciudad?',             'Our guide can show us around.',            'Nuestro guía nos puede mostrar los alrededores.'),
  ('trip',      'Where were you last week?',          '¿Dónde estuviste la semana pasada?',   'I was on a business trip.',                'Estaba en un viaje de negocios.'),
  ('vacation',  'Are you working in December?',       '¿Trabajas en diciembre?',              'No, I am on vacation.',                    'No, estoy de vacaciones.'),
  ('souvenir',  'What is in that bag?',               '¿Qué hay en esa bolsa?',               'A small souvenir from Peru.',              'Un pequeño recuerdo de Perú.'),
  ('currency',  'Can I pay in dollars?',              '¿Puedo pagar en dólares?',             'No, only in local currency.',              'No, solo en moneda local.'),
  ('museum',    'What can we do in the city?',        '¿Qué podemos hacer en la ciudad?',     'We can visit the gold museum.',            'Podemos visitar el museo del oro.'),
  ('abroad',    'Do you travel a lot?',               '¿Viajas mucho?',                       'Yes, I often travel abroad.',              'Sí, viajo al extranjero con frecuencia.');

insert into public.word_dialogues (word_id, line_a_en, line_a_es, line_b_en, line_b_es)
select w.id, d.line_a_en, d.line_a_es, d.line_b_en, d.line_b_es
  from seed_dialogues d
  join public.words w on w.english = d.english
 where not exists (select 1 from public.word_dialogues x
                    where x.word_id = w.id and x.line_b_en = d.line_b_en);

-- "Ruta completa" = todas las lecciones publicadas del catálogo.
update public.achievements a
   set threshold = n.total, description = format('Completa las %s lecciones del catálogo.', n.total)
  from (select count(*)::integer as total from public.lessons where is_published) n
 where a.code = 'all_lessons' and a.threshold <> n.total;

commit;
