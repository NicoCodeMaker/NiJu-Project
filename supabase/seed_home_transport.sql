-- =============================================================================
-- NiJu · Contenido adicional (versión 1.1): HOGAR y TRANSPORTE
-- OPCIONAL. Se ejecuta DESPUÉS de seed.sql (o solo; no depende de él).
-- 2 contextos × 2 lecciones × 6 palabras = 24 palabras nuevas, nivel A1.
-- Cada palabra tiene un ejemplo que contiene la palabra exacta (necesario para
-- "completar la oración"). Se puede ejecutar más de una vez sin duplicar datos.
--
-- Los textos en inglés de este archivo son la fuente de las pronunciaciones:
-- tools/audio/generate_audio.py lee la tabla `seed_words` de los archivos seed*.sql
-- para generar app/src/main/assets/audio/. Si cambias una palabra o una oración
-- aquí, vuelve a ejecutar ese script (ver docs/AUDIO.md).
-- =============================================================================

begin;

insert into public.contexts (slug, name, description, icon, sort_order) values
  ('home',      'Hogar',      'Habitaciones, muebles y objetos de tu casa.',          'home',      0),
  ('transport', 'Transporte', 'Moverte por la ciudad y pedir indicaciones.',          'transport', 4)
on conflict (slug) do nothing;

insert into public.lessons (context_id, slug, title, description, level, sort_order)
select c.id, v.slug, v.title, v.description, v.level::public.cefr_level, v.sort_order
  from (values
    ('home',      'home_rooms',          'Las habitaciones',   'Nombra los espacios de una casa.',          'A1', 1),
    ('home',      'home_objects',        'Cosas de la casa',   'Muebles y objetos que usas cada día.',      'A1', 2),
    ('transport', 'transport_city',      'En la ciudad',       'Medios de transporte y lugares.',           'A1', 1),
    ('transport', 'transport_directions','Cómo llegar',        'Pregunta y entiende indicaciones simples.', 'A1', 2)
  ) as v(ctx, slug, title, description, level, sort_order)
  join public.contexts c on c.slug = v.ctx
on conflict (slug) do nothing;

create temporary table seed_words (
  lesson text, pos smallint, english text, spanish text, ipa text,
  pos_tag text, level text, sentence_en text, sentence_es text
) on commit drop;

insert into seed_words values
  -- Hogar · Las habitaciones
  ('home_rooms', 1, 'kitchen',     'cocina',     '/ˈkɪtʃɪn/',      'noun', 'A1', 'I cook dinner in the kitchen.',       'Preparo la cena en la cocina.'),
  ('home_rooms', 2, 'bedroom',     'dormitorio', '/ˈbedruːm/',     'noun', 'A1', 'My bedroom is on the second floor.',  'Mi dormitorio está en el segundo piso.'),
  ('home_rooms', 3, 'bathroom',    'baño',       '/ˈbæθruːm/',     'noun', 'A1', 'The bathroom is next to the kitchen.','El baño está al lado de la cocina.'),
  ('home_rooms', 4, 'living room', 'sala',       '/ˈlɪvɪŋ ruːm/',  'noun', 'A1', 'We watch TV in the living room.',     'Vemos televisión en la sala.'),
  ('home_rooms', 5, 'garden',      'jardín',     '/ˈɡɑːrdn/',      'noun', 'A1', 'There are red flowers in the garden.','Hay flores rojas en el jardín.'),
  ('home_rooms', 6, 'stairs',      'escaleras',  '/sterz/',        'noun', 'A1', 'The stairs are very narrow.',         'Las escaleras son muy angostas.'),
  -- Hogar · Cosas de la casa
  ('home_objects', 1, 'bed',    'cama',     '/bed/',     'noun', 'A1', 'I make my bed every morning.',   'Tiendo mi cama cada mañana.'),
  ('home_objects', 2, 'chair',  'silla',    '/tʃer/',    'noun', 'A1', 'Please sit on that chair.',      'Por favor, siéntate en esa silla.'),
  ('home_objects', 3, 'door',   'puerta',   '/dɔːr/',    'noun', 'A1', 'Close the door, please.',        'Cierra la puerta, por favor.'),
  ('home_objects', 4, 'window', 'ventana',  '/ˈwɪndoʊ/', 'noun', 'A1', 'Open the window, it is hot.',    'Abre la ventana, hace calor.'),
  ('home_objects', 5, 'lamp',   'lámpara',  '/læmp/',    'noun', 'A1', 'The lamp is on the desk.',       'La lámpara está sobre el escritorio.'),
  ('home_objects', 6, 'key',    'llave',    '/kiː/',     'noun', 'A1', 'I cannot find my key.',          'No encuentro mi llave.'),
  -- Transporte · En la ciudad
  ('transport_city', 1, 'bus',     'autobús',   '/bʌs/',       'noun', 'A1', 'The bus arrives at seven.',        'El autobús llega a las siete.'),
  ('transport_city', 2, 'train',   'tren',      '/treɪn/',     'noun', 'A1', 'I take the train to work.',        'Tomo el tren para ir al trabajo.'),
  ('transport_city', 3, 'taxi',    'taxi',      '/ˈtæksi/',    'noun', 'A1', 'Let us take a taxi to the hotel.', 'Tomemos un taxi al hotel.'),
  ('transport_city', 4, 'station', 'estación',  '/ˈsteɪʃn/',   'noun', 'A1', 'The station is near here.',        'La estación está cerca de aquí.'),
  ('transport_city', 5, 'street',  'calle',     '/striːt/',    'noun', 'A1', 'My house is on this street.',      'Mi casa está en esta calle.'),
  ('transport_city', 6, 'bike',    'bicicleta', '/baɪk/',      'noun', 'A1', 'She goes to school by bike.',      'Ella va a la escuela en bicicleta.'),
  -- Transporte · Cómo llegar
  ('transport_directions', 1, 'map',      'mapa',       '/mæp/',        'noun',      'A1', 'Do you have a map of the city?', '¿Tienes un mapa de la ciudad?'),
  ('transport_directions', 2, 'left',     'izquierda',  '/left/',       'adverb',    'A1', 'Turn left at the corner.',       'Gira a la izquierda en la esquina.'),
  ('transport_directions', 3, 'right',    'derecha',    '/raɪt/',       'adverb',    'A1', 'Turn right at the traffic light.','Gira a la derecha en el semáforo.'),
  ('transport_directions', 4, 'straight', 'derecho',    '/streɪt/',     'adverb',    'A1', 'Go straight for two blocks.',    'Sigue derecho por dos cuadras.'),
  ('transport_directions', 5, 'corner',   'esquina',    '/ˈkɔːrnər/',   'noun',      'A1', 'Wait for me at the corner.',     'Espérame en la esquina.'),
  ('transport_directions', 6, 'far',      'lejos',      '/fɑːr/',       'adverb',    'A1', 'Is the airport far from here?',  '¿Está el aeropuerto lejos de aquí?');

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

-- El logro "Ruta completa" pedía las 6 lecciones del contenido v1.
-- Con este contenido adicional el catálogo tiene 10 lecciones.
update public.achievements
   set threshold = 10, description = 'Completa 10 lecciones.'
 where code = 'all_lessons' and threshold = 6;

commit;
