package jdbi;

import java.util.List;

import org.jooby.Jooby;
import org.jooby.Results;
import org.jooby.jdbc.Jdbc;
import org.jooby.jdbi.Jdbi;
import org.jooby.json.Jackson;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;

import com.typesafe.config.Config;

public class App extends Jooby {

  {
    use(new Jackson());

    use(new Jdbc());

    use(new Jdbi()
        // 1 dbi ready
        .doWith((final DBI dbi, final Config conf) -> {
          // 2 open a new handle
          try (Handle handle = dbi.open()) {
            // 3. execute script
            handle.execute(conf.getString("schema"));
          }
        }));

    /** Pet API. */
    path("/api/pets", () -> {
      /** List pets. */
      get(req -> {
        return require(DBI.class).inTransaction((handle, status) -> {
          PetRepository repo = handle.attach(PetRepository.class);
          List<Pet> pets = repo.list();
          return pets;
        });
      });
      /** Get a pet by ID. */
      get("/:id", req -> {
        return require(DBI.class).inTransaction((handle, status) -> {
          int id = req.param("id").intValue();

          PetRepository repo = handle.attach(PetRepository.class);
          Pet pet = repo.findById(id);
          return pet;
        });
      });
      /** Create a pet. */
      post(req -> {
        return require(DBI.class).inTransaction((handle, status) -> {
          // read from HTTP body
          Pet pet = req.body(Pet.class);

          PetRepository repo = handle.attach(PetRepository.class);
          int petId = repo.insert(pet);
          pet.setId(petId);
          return pet;
        });
      });
      /** Update a pet. */
      put(req -> {
        return require(DBI.class).inTransaction((handle, status) -> {
          // read from HTTP body
          Pet pet = req.body(Pet.class);

          PetRepository repo = handle.attach(PetRepository.class);
          repo.update(pet);
          return pet;
        });
      });
      /** Delete a pet by ID. */
      delete("/:id", req -> {
        return require(DBI.class).inTransaction((handle, status) -> {
          int id = req.param("id").intValue();

          PetRepository repo = handle.attach(PetRepository.class);
          repo.deleteById(id);
          return Results.noContent();
        });
      });
    });
  }

  public static void main(final String[] args) {
    run(App::new, args);
  }

}
