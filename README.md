[![Build Status](https://travis-ci.org/jooby-project/jdbi-guide.svg?branch=master)](https://travis-ci.org/jooby-project/jdbi-guide)
# jdbi guide

In this guide you will learn how to build a **JSON API** for ```Pets``` and persist them into a **relational database** using the [jdbi](https://github.com/jooby-project/jooby/tree/master/jooby-jdbi) module.

[JDBI](http://jdbi.org/) is a SQL convenience library for Java. It attempts to expose relational database access in idiomatic Java, using collections, beans, and so on, while maintaining the same level of detail as JDBC. It exposes two different style APIs, a fluent style and a sql object style.

# requirements

Make sure you have the following installed on your computer:

* A text editor or IDE
* [JDK 8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or later
* [Maven 3+](http://maven.apache.org/)

# ready

Open a terminal/console and paste:

```bash
mvn archetype:generate -B -DgroupId=org.jooby.guides -DartifactId=jdbi-guide -Dversion=1.0 -DarchetypeArtifactId=jooby-archetype -DarchetypeGroupId=org.jooby -DarchetypeVersion=1.4.0
```

Enter the application directory:

```
cd jdbi-guide
```

# dependencies

## jackson

Add the [jackson](https://github.com/jooby-project/jooby/tree/master/jooby-jackson) dependency to your project:

```xml
<dependency>
  <groupId>org.jooby</groupId>
  <artifactId>jooby-jackson</artifactId>
  <version>1.4.0</version>
</dependency>
```

Go to `App.java` and add the module:

```java
import org.jooby.json.Jackson;
...
{
  use(new Jackson());
}
```

## jdbi

Add the [jdbi](https://github.com/jooby-project/jooby/tree/master/jooby-jdbi) dependency to your project:

```xml
<dependency>
  <groupId>org.jooby</groupId>
  <artifactId>jooby-jdbi</artifactId>
  <version>1.4.0</version>
</dependency>
```

Import and use the module in `App.java`:

```java
import org.jooby.jdbi.Jdbi;
...
{
  ...
  use(new Jdbi());
}
```

# create a pet object

Let's create a simple ```Pet``` class with an ```id```, ```name``` and getters/setters for them, like:

```java
package jdbi;

public class Pet {

  private Integer id;

  private String name;

  public Integer getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setId(final Integer id) {
    this.id = id;
  }

  public void setName(final String name) {
    this.name = name;
  }

}
```

# connect to database

The [jdbi](https://github.com/jooby-project/jooby/tree/master/jooby-jdbi) module extends the [jdbc](https://github.com/jooby-project/jooby/tree/master/jooby-jdbc) module. The [jdbc](https://github.com/jooby-project/jooby/tree/master/jooby-jdbc) module give us access to relational databases and exports a [Hikari](https://github.com/brettwooldridge/HikariCP) database connection pool.

To connect to a database, we have to define our database connection properties in ```conf/application.conf```:

```
db = mem
```

The ```mem``` or ```fs``` are special databases. In order to use them we need the [h2](http://www.h2database.com) driver, so let's add it:

```xml
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
</dependency>
```

> **NOTE**: If you want to connect to a database other than the embedded ```mem``` or ```fs```, like e.g. `mySQL`, then you'll have to add the ```mySQL Java Driver``` to your project and define the connection properties like this:
>
> ```
> db.url = "jdbc:mysql//localhost/pets"
> db.user = "user"
> db.password = "password"
> ```

## creating a schema

We are going to create a database schema at application startup time:

* Define a `schema` property in ```conf/application.conf``` like this:

```
schema = """

  create table if not exists pets (

    id int not null auto_increment,

    name varchar(255) not null,

    primary key (id)

  );
"""
```

* Execute the script in `App.java`:

```java
import org.skife.jdbi.v2.Handle;
...
{
  use(new Jdbi()
    // 1 dbi ready
    .doWith((DBI dbi, Config conf) -> {
      // 2 open a new handle
      try (Handle handle = dbi.open()) {
        // 3. execute script
        handle.execute(conf.getString("schema"));
      }
    }));
}
```

**1)** The [.doWith](http://jooby.org/apidocs/org/jooby/jdbc/Jdbc.html#doWith-java.util.function.BiConsumer-) is a callback method which is executed when `DBI` is ready.

**2)** We open a new `Handle` for running our script, which is automatically released with the ```try-with-resources``` statement.

**3)** We execute the create schema script.

With a database ready, we are going to build our *JSON API*.

> **TIP**: There is [flyway](https://github.com/jooby-project/jooby/tree/master/jooby-flyway) module for database migrations.

## creating a repository

[The SQL Object API](http://jdbi.org/sql_object_overview/) provides a declarative mechanism for a common [JDBI](http://jdbi.org/) usage – creation of DAO type objects where one method generally equates to one SQL statement. To use the SQL Object API, create an interface annotated to declare the desired behavior, like this:

```java
package jdbi;

import java.io.Closeable;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.helpers.MapResultAsBean;

/**
 * Basic CRUD operations around {@link Pet}.
 */
public interface PetRepository extends Closeable {

  /**
   * @return List all pets.
   */
  @SqlQuery("select * from pets")
  @MapResultAsBean
  List<Pet> list();

  /**
   * Find a pet by ID.
   *
   * @param id Pet ID.
   * @return A pet.
   */
  @SqlQuery("select * from pets where id = :id")
  @MapResultAsBean
  Pet findById(@Bind("id") int id);

  /**
   * Insert a new pet.
   *
   * @param pet A pet.
   * @return The generated key.
   */
  @SqlUpdate("insert into pets (name) values (:pet.name)")
  @GetGeneratedKeys
  int insert(@BindBean("pet") Pet pet);

  /**
   * Update a pet by ID.
   *
   * @param pet Pet to update.
   */
  @SqlUpdate("update pets set name=:pet.name where id=:pet.id")
  void update(@BindBean("pet") Pet pet);

  /**
   * Delete a pet by ID.
   *
   * @param id Pet ID.
   */
  @SqlUpdate("delete pets where id = :id")
  void deleteById(@Bind("id") int id);
}
```

# routes

## listing pets

```java
{
  get("/api/pets", req -> {
    // 1 get dbi and start a new transaction
    return require(DBI.class).inTransaction((handle, status) -> {
      // 2 attach the repository to jdbi handle
      PetRepository repo = handle.attach(PetRepository.class);

      // 3 list all pets
      List<Pet> pets = repo.list();
      return pets;
    });
  });
}
```

## get a pet by ID

```java
{
  get("/api/pets/:id", req -> {
    // 1 get dbi and start a new transaction
    return require(DBI.class).inTransaction((handle, status) -> {
      // 2 get ID from HTTP request
      int id = req.param("id").intValue();

      // 3 attach the repository to jdbi handle
      PetRepository repo = handle.attach(PetRepository.class);

      // 4 get a pet by ID
      Pet pet = repo.findById(id);

      if (pet == null) {
        // 5 generate 404 for invalid pet IDs
        throw new Err(Status.NOT_FOUND);
      }
      return pet;
    });
  });
}
```

Try it:

```
http://localhost:8080/pets/1
```

You'll see an error page because we didn't persist any pet yet. Let's see how to save one.

## save a pet

So far, we've seen how to query pets by ID or listing all them, it is time to see how to create a new pet:

```java
{
  post("/api/pets", req -> {
    // 1 get dbi and start a new transaction
    return require(DBI.class).inTransaction((handle, status) -> {
      // 2 read pet from JSON HTTP body
      Pet pet = req.body(Pet.class);

      // 3 attach respository to jdbi handle
      PetRepository repo = handle.attach(PetRepository.class);

      // 4 insert pet and retrieve generated ID
      int petId = repo.insert(pet);
      pet.setId(petId);

      return pet;
    });
  });
}
```

## update a pet

```java
{
  put("/api/pets", req -> {
    // 1 get dbi and start a new transaction
    return require(DBI.class).inTransaction((handle, status) -> {
      // 2 read pet from JSON HTTP body
      Pet pet = req.body(Pet.class);

      // 3 attach repository to jdbi handle
      PetRepository repo = handle.attach(PetRepository.class);

      // 4 update pet
      repo.update(pet);

      return pet;
    });
  });
}
```

## delete a pet by ID

```java
{
  delete("/api/pets/:id", req -> {
    // 1 get dbi and start a new transaction
    return require(DBI.class).inTransaction((handle, status) -> {
      // 2 read pet id from HTTP request
      int id = req.param("id").intValue();

      // 3 attach repository to jdbi handle
      PetRepository repo = handle.attach(PetRepository.class);

      // 4 delete pet by ID
      repo.deleteById(id);

      return Results.noContent();
    });
  });
}
```

# quick preview

The API is ready, let's see how it looks like:

```java
{
  /** JSON supports . */
  use(new Jackson());

  /** Create db schema. */
  use(new Jdbi().doWith((dbi, conf) -> {
    try (Handle handle = dbi.open()) {
      handle.execute(conf.getString("schema"));
    }
  }));

  /** List pets. */
  get("/api/pets", req -> {
    ...
  });

  /** Get a pet by ID. */
  get("/api/pets/:id", req -> {
    ...
  });

  /** Create a pet. */
  post("/api/pets", req -> {
    ...
  });

  /** Update a pet. */
  put("/api/pets", req -> {
    ...
  });

  /** Delete a pet by ID. */
  delete("/api/pets/:id", req -> {
    ...
  });
}
```

Not bad, huh?

But did you notice that we have to repeat the `/api/pets` pattern for each of our routes?

Let's fix that with [Jooby.use(String)](http://jooby.org/apidocs/org/jooby/Jooby.html#use-java.lang.String-):

```java
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
    use("/api/pets")
        /** List pets. */
        .get(req -> {
          return require(DBI.class).inTransaction((handle, status) -> {
            PetRepository repo = handle.attach(PetRepository.class);
            List<Pet> pets = repo.list();
            return pets;
          });
        })
        /** Get a pet by ID. */
        .get("/:id", req -> {
          return require(DBI.class).inTransaction((handle, status) -> {
            int id = req.param("id").intValue();

            PetRepository repo = handle.attach(PetRepository.class);
            Pet pet = repo.findById(id);
            return pet;
          });
        })
        /** Create a pet. */
        .post(req -> {
          return require(DBI.class).inTransaction((handle, status) -> {
            // read from HTTP body
            Pet pet = req.body(Pet.class);

            PetRepository repo = handle.attach(PetRepository.class);
            int petId = repo.insert(pet);
            pet.setId(petId);
            return pet;
          });
        })
        /** Update a pet. */
        .put(req -> {
          return require(DBI.class).inTransaction((handle, status) -> {
            // read from HTTP body
            Pet pet = req.body(Pet.class);

            PetRepository repo = handle.attach(PetRepository.class);
            repo.update(pet);
            return pet;
          });
        })
        /** Delete a pet by ID. */
        .delete("/:id", req -> {
          return require(DBI.class).inTransaction((handle, status) -> {
            int id = req.param("id").intValue();

            PetRepository repo = handle.attach(PetRepository.class);
            repo.deleteById(id);
            return Results.noContent();
          });
        });
  }

  public static void main(final String[] args) {
    run(App::new, args);
  }

}
```

That's better! The ```use``` method has many meanings in **Jooby**, If we use pass a ```String``` we can group routes under the same path pattern.

# conclusion

As you've already seen, building an API that saves data in a **database** is very easy. The code looks clean and simple thanks to the [jdbi](https://github.com/jooby-project/jooby/tree/master/jooby-jdbi) module.

The [jdbi](https://github.com/jooby-project/jooby/tree/master/jooby-jdbi) module makes perfect sense if you want to have full control of your SQL queries, or if you prefer not to use **ORM** tools.

# source code

* Complete source code available at: [jooby-project/jdbi-guide](https://github.com/jooby-project/jdbi-guide)

# help and support

* Discuss, share ideas, ask questions at [group](https://groups.google.com/forum/#!forum/jooby-project) or [gitter](https://gitter.im/jooby-project/jooby)
* Follow us at [@joobyproject](https://twitter.com/joobyproject) and [GitHub](https://github.com/jooby-project/jooby/tree/master)

