-- =============================================================================
-- NiJu · Contenido inicial (versión 1)
-- OPCIONAL (datos de demostración y pruebas).
-- 3 contextos × 2 lecciones × 6 palabras = 36 palabras, cada una con un
-- ejemplo que contiene la palabra exacta (necesario para "completar la oración").
-- Nivel A1–A2. Se puede ejecutar más de una vez sin duplicar datos.
-- =============================================================================

begin;

insert into public.contexts (slug, name, description, icon, sort_order) values
  ('restaurant',  'Restaurante',  'Pide una mesa, ordena comida y paga la cuenta.', 'restaurant',  1),
  ('supermarket', 'Supermercado', 'Encuentra productos y paga en la caja.',          'supermarket', 2),
  ('airport',     'Aeropuerto',   'Haz el check-in, aborda y entiende los avisos.',  'airport',     3)
on conflict (slug) do nothing;

insert into public.lessons (context_id, slug, title, description, level, sort_order)
select c.id, v.slug, v.title, v.description, v.level::public.cefr_level, v.sort_order
  from (values
    ('restaurant',  'restaurant_table',   'Pedir una mesa',          'Llegar al restaurante y ordenar.',        'A1', 1),
    ('restaurant',  'restaurant_pay',     'Comer y pagar',           'Hablar de la comida y pedir la cuenta.',  'A1', 2),
    ('supermarket', 'supermarket_find',   'Encontrar productos',     'Moverte por los pasillos.',                'A1', 1),
    ('supermarket', 'supermarket_pay',    'En la caja',              'Precios, bolsas y recibos.',               'A1', 2),
    ('airport',     'airport_before',     'Antes de volar',          'Documentos, equipaje y check-in.',         'A2', 1),
    ('airport',     'airport_gate',       'En la puerta de embarque','Abordar, asientos y horarios.',            'A2', 2)
  ) as v(ctx, slug, title, description, level, sort_order)
  join public.contexts c on c.slug = v.ctx
on conflict (slug) do nothing;

-- Palabras + ejemplo + lección + posición
create temporary table seed_words (
  lesson text, pos smallint, english text, spanish text, ipa text,
  pos_tag text, level text, sentence_en text, sentence_es text
) on commit drop;

insert into seed_words values
  -- Restaurante · Pedir una mesa
  ('restaurant_table', 1, 'menu',        'menú',     '/ˈmenjuː/',       'noun', 'A1', 'Can I see the menu, please?',      '¿Puedo ver el menú, por favor?'),
  ('restaurant_table', 2, 'waiter',      'mesero',   '/ˈweɪtər/',       'noun', 'A1', 'The waiter brings us two glasses.', 'El mesero nos trae dos vasos.'),
  ('restaurant_table', 3, 'table',       'mesa',     '/ˈteɪbəl/',       'noun', 'A1', 'We need a table for two.',         'Necesitamos una mesa para dos.'),
  ('restaurant_table', 4, 'reservation', 'reserva',  '/ˌrezərˈveɪʃən/', 'noun', 'A1', 'I have a reservation at eight.',   'Tengo una reserva a las ocho.'),
  ('restaurant_table', 5, 'order',       'pedir',    '/ˈɔːrdər/',       'verb', 'A1', 'Are you ready to order?',          '¿Están listos para pedir?'),
  ('restaurant_table', 6, 'water',       'agua',     '/ˈwɔːtər/',       'noun', 'A1', 'A glass of water, please.',        'Un vaso de agua, por favor.'),
  -- Restaurante · Comer y pagar
  ('restaurant_pay', 1, 'bill',      'cuenta',    '/bɪl/',       'noun',      'A1', 'Can we have the bill, please?',  '¿Nos trae la cuenta, por favor?'),
  ('restaurant_pay', 2, 'tip',       'propina',   '/tɪp/',       'noun',      'A1', 'We left a tip on the table.',    'Dejamos una propina en la mesa.'),
  ('restaurant_pay', 3, 'delicious', 'delicioso', '/dɪˈlɪʃəs/',  'adjective', 'A1', 'This soup is delicious.',        'Esta sopa está deliciosa.'),
  ('restaurant_pay', 4, 'spicy',     'picante',   '/ˈspaɪsi/',   'adjective', 'A1', 'Is the chicken spicy?',          '¿El pollo es picante?'),
  ('restaurant_pay', 5, 'dessert',   'postre',    '/dɪˈzɜːrt/',  'noun',      'A1', 'I want ice cream for dessert.',  'Quiero helado de postre.'),
  ('restaurant_pay', 6, 'pay',       'pagar',     '/peɪ/',       'verb',      'A1', 'Can I pay by card?',             '¿Puedo pagar con tarjeta?'),
  -- Supermercado · Encontrar productos
  ('supermarket_find', 1, 'cart',  'carrito', '/kɑːrt/',  'noun',      'A1', 'I put the milk in the cart.',      'Pongo la leche en el carrito.'),
  ('supermarket_find', 2, 'aisle', 'pasillo', '/aɪl/',    'noun',      'A1', 'The bread is in aisle five.',      'El pan está en el pasillo cinco.'),
  ('supermarket_find', 3, 'bread', 'pan',     '/bred/',   'noun',      'A1', 'I buy fresh bread every day.',     'Compro pan fresco todos los días.'),
  ('supermarket_find', 4, 'milk',  'leche',   '/mɪlk/',   'noun',      'A1', 'We need milk and eggs.',           'Necesitamos leche y huevos.'),
  ('supermarket_find', 5, 'shelf', 'estante', '/ʃelf/',   'noun',      'A1', 'The rice is on the top shelf.',    'El arroz está en el estante de arriba.'),
  ('supermarket_find', 6, 'fresh', 'fresco',  '/freʃ/',   'adjective', 'A1', 'These apples are very fresh.',     'Estas manzanas están muy frescas.'),
  -- Supermercado · En la caja
  ('supermarket_pay', 1, 'cashier',   'cajero',    '/kæˈʃɪr/',     'noun',      'A1', 'The cashier gives me the change.',  'El cajero me da el cambio.'),
  ('supermarket_pay', 2, 'receipt',   'recibo',    '/rɪˈsiːt/',    'noun',      'A1', 'Do you want the receipt?',          '¿Quiere el recibo?'),
  ('supermarket_pay', 3, 'bag',       'bolsa',     '/bæɡ/',        'noun',      'A1', 'Do you need a bag?',                '¿Necesita una bolsa?'),
  ('supermarket_pay', 4, 'cheap',     'barato',    '/tʃiːp/',      'adjective', 'A1', 'Bananas are cheap this week.',      'Los bananos están baratos esta semana.'),
  ('supermarket_pay', 5, 'expensive', 'caro',      '/ɪkˈspensɪv/', 'adjective', 'A1', 'This cheese is too expensive.',     'Este queso es demasiado caro.'),
  ('supermarket_pay', 6, 'discount',  'descuento', '/ˈdɪskaʊnt/',  'noun',      'A1', 'There is a discount on coffee.',    'Hay un descuento en el café.'),
  -- Aeropuerto · Antes de volar
  ('airport_before', 1, 'passport',      'pasaporte',       '/ˈpæspɔːrt/',     'noun',   'A2', 'Please show me your passport.',          'Por favor, muéstreme su pasaporte.'),
  ('airport_before', 2, 'flight',        'vuelo',           '/flaɪt/',         'noun',   'A2', 'My flight leaves at noon.',              'Mi vuelo sale al mediodía.'),
  ('airport_before', 3, 'luggage',       'equipaje',        '/ˈlʌɡɪdʒ/',       'noun',   'A2', 'My luggage is very heavy.',              'Mi equipaje es muy pesado.'),
  ('airport_before', 4, 'ticket',        'boleto',          '/ˈtɪkɪt/',        'noun',   'A2', 'I bought my ticket online.',             'Compré mi boleto por internet.'),
  ('airport_before', 5, 'check in',      'registrarse',     '/tʃek ɪn/',       'phrase', 'A2', 'We need to check in two hours early.',   'Necesitamos registrarnos dos horas antes.'),
  ('airport_before', 6, 'boarding pass', 'pase de abordar', '/ˈbɔːrdɪŋ pæs/',  'noun',   'A2', 'Keep your boarding pass with you.',      'Mantenga su pase de abordar con usted.'),
  -- Aeropuerto · En la puerta de embarque
  ('airport_gate', 1, 'gate',      'puerta de embarque', '/ɡeɪt/',       'noun', 'A2', 'Our plane leaves from gate 12.',   'Nuestro avión sale de la puerta 12.'),
  ('airport_gate', 2, 'delay',     'retraso',            '/dɪˈleɪ/',     'noun', 'A2', 'There is a delay of one hour.',    'Hay un retraso de una hora.'),
  ('airport_gate', 3, 'seat',      'asiento',            '/siːt/',       'noun', 'A2', 'Excuse me, this is my seat.',      'Disculpe, este es mi asiento.'),
  ('airport_gate', 4, 'board',     'abordar',            '/bɔːrd/',      'verb', 'A2', 'Passengers can board now.',        'Los pasajeros pueden abordar ahora.'),
  ('airport_gate', 5, 'arrival',   'llegada',            '/əˈraɪvəl/',   'noun', 'A2', 'The arrival time is six o''clock.', 'La hora de llegada es a las seis.'),
  ('airport_gate', 6, 'departure', 'salida',             '/dɪˈpɑːrtʃər/','noun', 'A2', 'Check the departure screen.',      'Revisa la pantalla de salidas.');

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

commit;
