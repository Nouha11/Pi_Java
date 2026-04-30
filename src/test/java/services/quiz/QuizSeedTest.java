package services.quiz;

import models.quiz.Choice;
import models.quiz.Question;
import models.quiz.Quiz;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run this test once to seed the database with demo quizzes.
 * It is safe to run multiple times — it always inserts fresh records.
 *
 * To run: right-click the class in IntelliJ → Run 'QuizSeedTest'
 */
class QuizSeedTest {

    static QuizService     quizService;
    static QuestionService questionService;
    static ChoiceService   choiceService;

    @BeforeAll
    static void setup() {
        quizService     = new QuizService();
        questionService = new QuestionService();
        choiceService   = new ChoiceService();
    }

    // ── Helper ────────────────────────────────────────────────

    /**
     * Creates a full quiz with questions and choices in one call.
     *
     * @param title       quiz title
     * @param description quiz description
     * @param questions   array of question data: each entry is
     *                    { text, difficulty, xp, correct, wrong1, wrong2, wrong3 }
     */
    private void createQuiz(String title, String description, Object[][] questions) {
        Quiz quiz = new Quiz(title, description);
        quizService.createQuiz(quiz);

        // Fetch back to get the generated ID
        int quizId = quizService.getAllQuizzes().stream()
                .filter(q -> q.getTitle().equals(title))
                .mapToInt(Quiz::getId)
                .max()
                .orElseThrow(() -> new RuntimeException("Quiz not found after insert: " + title));

        for (Object[] q : questions) {
            String text       = (String)  q[0];
            String difficulty = (String)  q[1];
            int    xp         = (int)     q[2];
            String correct    = (String)  q[3];
            String wrong1     = (String)  q[4];
            String wrong2     = (String)  q[5];
            String wrong3     = (String)  q[6];

            Question question = new Question(text, xp, difficulty, quizId);
            question.setUpdatedAt(LocalDateTime.now());
            questionService.createQuestion(question);

            choiceService.createChoice(new Choice(correct, true,  question.getId()));
            choiceService.createChoice(new Choice(wrong1,  false, question.getId()));
            choiceService.createChoice(new Choice(wrong2,  false, question.getId()));
            choiceService.createChoice(new Choice(wrong3,  false, question.getId()));
        }

        System.out.println("✅ Created quiz: " + title + " (" + questions.length + " questions)");
    }

    // ── Seed tests ────────────────────────────────────────────

    @Test
    void seedJavaBasicsQuiz() {
        createQuiz("Java Basics", "Fundamental Java programming concepts.", new Object[][] {
            { "What is the default value of an int in Java?",                    "EASY",   10, "0",           "null",       "-1",         "undefined"   },
            { "Which keyword is used to define a constant in Java?",             "EASY",   10, "final",       "const",      "static",     "immutable"   },
            { "What does JVM stand for?",                                        "EASY",   10, "Java Virtual Machine", "Java Variable Method", "Java Verified Module", "Just Virtual Memory" },
            { "Which of these is NOT a primitive type in Java?",                 "MEDIUM", 20, "String",      "int",        "boolean",    "char"        },
            { "What is the size of a long in Java?",                             "MEDIUM", 20, "64 bits",     "32 bits",    "128 bits",   "16 bits"     },
            { "Which method is the entry point of a Java application?",          "EASY",   10, "main()",      "start()",    "run()",      "init()"      },
            { "What does the 'static' keyword mean in Java?",                    "MEDIUM", 20, "Belongs to the class, not an instance", "Cannot be changed", "Runs at startup", "Is private" },
            { "Which collection does NOT allow duplicate elements?",             "MEDIUM", 20, "Set",         "List",       "ArrayList",  "LinkedList"  },
        });
        assertTrue(true);
    }

    @Test
    void seedOOPConceptsQuiz() {
        createQuiz("OOP Concepts", "Object-Oriented Programming principles.", new Object[][] {
            { "What are the four pillars of OOP?",                               "MEDIUM", 20, "Encapsulation, Inheritance, Polymorphism, Abstraction", "Classes, Objects, Methods, Fields", "Loops, Conditions, Functions, Variables", "Compile, Link, Run, Debug" },
            { "What is encapsulation?",                                          "EASY",   10, "Hiding internal state and requiring all interaction through methods", "Inheriting from a parent class", "Having multiple forms", "Hiding the implementation" },
            { "Which keyword is used for inheritance in Java?",                  "EASY",   10, "extends",     "implements", "inherits",   "super"       },
            { "What is polymorphism?",                                           "MEDIUM", 20, "The ability of an object to take many forms", "Creating multiple classes", "Hiding data", "Overloading constructors" },
            { "What is an abstract class?",                                      "MEDIUM", 20, "A class that cannot be instantiated and may have abstract methods", "A class with no methods", "A class that is private", "A class with only static methods" },
            { "What is the difference between an interface and an abstract class?", "HARD", 30, "An interface has no implementation; abstract class can have some", "They are the same", "Abstract class has no fields", "Interface can be instantiated" },
            { "What does 'super' keyword do in Java?",                           "MEDIUM", 20, "Refers to the parent class", "Refers to the current class", "Creates a new object", "Calls a static method" },
            { "What is method overriding?",                                      "MEDIUM", 20, "Providing a new implementation of a parent class method in a subclass", "Defining two methods with the same name", "Calling a method twice", "Hiding a method" },
        });
        assertTrue(true);
    }

    @Test
    void seedDatabaseFundamentalsQuiz() {
        createQuiz("Database Fundamentals", "Core SQL and database concepts.", new Object[][] {
            { "What does SQL stand for?",                                        "EASY",   10, "Structured Query Language", "Simple Query Language", "Standard Question Language", "Sequential Query Logic" },
            { "Which SQL command retrieves data from a table?",                  "EASY",   10, "SELECT",      "GET",        "FETCH",      "READ"        },
            { "What is a primary key?",                                          "EASY",   10, "A unique identifier for each row in a table", "The first column of a table", "A foreign reference", "An index on a column" },
            { "What is a foreign key?",                                          "MEDIUM", 20, "A key that references the primary key of another table", "A key that is not primary", "A duplicate key", "A key used for sorting" },
            { "Which SQL clause filters rows?",                                  "EASY",   10, "WHERE",       "HAVING",     "GROUP BY",   "ORDER BY"    },
            { "What does JOIN do in SQL?",                                       "MEDIUM", 20, "Combines rows from two or more tables based on a related column", "Deletes duplicate rows", "Sorts the result", "Groups rows together" },
            { "What is normalization in databases?",                             "HARD",   30, "Organizing data to reduce redundancy and improve integrity", "Making queries faster", "Adding indexes", "Encrypting data" },
            { "Which SQL command removes all rows from a table without deleting it?", "MEDIUM", 20, "TRUNCATE", "DELETE",    "DROP",       "REMOVE"      },
        });
        assertTrue(true);
    }

    @Test
    void seedWebDevelopmentQuiz() {
        createQuiz("Web Development Basics", "HTML, CSS, and JavaScript fundamentals.", new Object[][] {
            { "What does HTML stand for?",                                       "EASY",   10, "HyperText Markup Language", "High Transfer Markup Language", "HyperText Management Language", "Hyper Transfer Markup Logic" },
            { "Which HTML tag is used for the largest heading?",                 "EASY",   10, "<h1>",        "<h6>",       "<header>",   "<title>"     },
            { "What does CSS stand for?",                                        "EASY",   10, "Cascading Style Sheets", "Creative Style System", "Computer Style Sheets", "Colorful Style Syntax" },
            { "Which CSS property changes text color?",                          "EASY",   10, "color",       "text-color", "font-color", "foreground"  },
            { "What is the correct way to declare a variable in modern JavaScript?", "MEDIUM", 20, "let x = 5;", "var x = 5;", "variable x = 5;", "x := 5;" },
            { "What does DOM stand for?",                                        "MEDIUM", 20, "Document Object Model", "Data Object Management", "Document Oriented Method", "Dynamic Object Model" },
            { "Which HTTP method is used to send data to a server?",             "MEDIUM", 20, "POST",        "GET",        "SEND",       "PUSH"        },
            { "What is the purpose of the <meta> tag in HTML?",                  "MEDIUM", 20, "Provides metadata about the HTML document", "Creates a menu", "Defines a method", "Links a stylesheet" },
        });
        assertTrue(true);
    }

    @Test
    void seedGeneralKnowledgeQuiz() {
        createQuiz("General Knowledge", "A mix of science, history, and geography questions.", new Object[][] {
            { "What is the capital of France?",                                  "EASY",   10, "Paris",       "London",     "Berlin",     "Madrid"      },
            { "Which planet is known as the Red Planet?",                        "EASY",   10, "Mars",        "Venus",      "Jupiter",    "Saturn"      },
            { "What is the chemical symbol for water?",                          "EASY",   10, "H2O",         "HO",         "CO2",        "NaCl"        },
            { "In which year did World War II end?",                             "MEDIUM", 20, "1945",        "1943",       "1944",       "1946"        },
            { "What is the largest ocean on Earth?",                             "EASY",   10, "Pacific Ocean", "Atlantic Ocean", "Indian Ocean", "Arctic Ocean" },
            { "Who painted the Mona Lisa?",                                      "EASY",   10, "Leonardo da Vinci", "Michelangelo", "Raphael",  "Picasso"     },
            { "What is the speed of light in a vacuum (approx)?",               "MEDIUM", 20, "300,000 km/s", "150,000 km/s", "450,000 km/s", "100,000 km/s" },
            { "Which country has the largest population in the world?",          "EASY",   10, "India",       "China",      "USA",        "Indonesia"   },
        });
        assertTrue(true);
    }

    @Test
    void seedDataStructuresQuiz() {
        createQuiz("Data Structures", "Common data structures and their properties.", new Object[][] {
            { "Which data structure uses LIFO order?",                           "EASY",   10, "Stack",       "Queue",      "LinkedList", "Tree"        },
            { "Which data structure uses FIFO order?",                           "EASY",   10, "Queue",       "Stack",      "Heap",       "Graph"       },
            { "What is the time complexity of binary search?",                   "MEDIUM", 20, "O(log n)",    "O(n)",       "O(n²)",      "O(1)"        },
            { "What is a linked list?",                                          "EASY",   10, "A sequence of nodes where each node points to the next", "An array with links", "A sorted array", "A tree with two children" },
            { "What is a hash table used for?",                                  "MEDIUM", 20, "Fast key-value lookups", "Sorting data", "Storing trees", "Traversing graphs" },
            { "What is the worst-case time complexity of quicksort?",            "HARD",   30, "O(n²)",       "O(n log n)", "O(n)",       "O(log n)"    },
            { "Which traversal visits the root node first?",                     "MEDIUM", 20, "Pre-order",   "In-order",   "Post-order", "Level-order" },
            { "What is a binary search tree property?",                          "MEDIUM", 20, "Left child < parent < right child", "All nodes are equal", "Right child < parent", "Parent < both children" },
        });
        assertTrue(true);
    }

    @Test
    void seedMathQuiz() {
        createQuiz("Mathematics", "Basic to intermediate math questions.", new Object[][] {
            { "What is the value of π (pi) to two decimal places?",             "EASY",   10, "3.14",        "3.12",       "3.16",       "3.41"        },
            { "What is the square root of 144?",                                 "EASY",   10, "12",          "11",         "13",         "14"          },
            { "What is 15% of 200?",                                             "EASY",   10, "30",          "25",         "35",         "20"          },
            { "What is the formula for the area of a circle?",                   "MEDIUM", 20, "πr²",         "2πr",        "πd",         "r²"          },
            { "What is the derivative of x²?",                                   "MEDIUM", 20, "2x",          "x",          "2",          "x²"          },
            { "What is the Pythagorean theorem?",                                "EASY",   10, "a² + b² = c²", "a + b = c", "a² - b² = c", "a × b = c²" },
            { "What is the sum of angles in a triangle?",                        "EASY",   10, "180°",        "360°",       "90°",        "270°"        },
            { "What is a prime number?",                                         "EASY",   10, "A number divisible only by 1 and itself", "An even number", "A number greater than 100", "A multiple of 2" },
        });
        assertTrue(true);
    }

    @Test
    void seedNetworkingQuiz() {
        createQuiz("Computer Networking", "Networking protocols and concepts.", new Object[][] {
            { "What does IP stand for?",                                         "EASY",   10, "Internet Protocol", "Internal Process", "Integrated Port", "Internet Port" },
            { "What is the purpose of DNS?",                                     "MEDIUM", 20, "Translates domain names to IP addresses", "Encrypts web traffic", "Assigns IP addresses", "Routes packets" },
            { "Which protocol is used for secure web browsing?",                 "EASY",   10, "HTTPS",       "HTTP",       "FTP",        "SMTP"        },
            { "What is a MAC address?",                                          "MEDIUM", 20, "A unique hardware identifier for a network interface", "A software address", "A type of IP address", "A routing protocol" },
            { "What does TCP stand for?",                                        "EASY",   10, "Transmission Control Protocol", "Transfer Connection Protocol", "Terminal Control Process", "Timed Connection Protocol" },
            { "What is the difference between TCP and UDP?",                     "HARD",   30, "TCP is reliable and ordered; UDP is faster but unreliable", "TCP is faster; UDP is reliable", "They are the same", "UDP uses more bandwidth" },
            { "What is a subnet mask used for?",                                 "MEDIUM", 20, "Dividing an IP address into network and host portions", "Encrypting data", "Assigning MAC addresses", "Routing between networks" },
            { "Which layer of the OSI model handles routing?",                   "HARD",   30, "Network layer (Layer 3)", "Transport layer (Layer 4)", "Data link layer (Layer 2)", "Application layer (Layer 7)" },
        });
        assertTrue(true);
    }

    // ── Verify all quizzes were created ───────────────────────

    @Test
    void verifyAllQuizzesExist() {
        List<Quiz> all = quizService.getAllQuizzes();
        System.out.println("\n=== Quizzes in database ===");
        all.forEach(q -> System.out.println("  [" + q.getId() + "] " + q.getTitle()
                + " — " + q.getQuestionCount() + " questions"));
        System.out.println("Total: " + all.size() + " quizzes");
        assertTrue(all.size() >= 8, "Expected at least 8 quizzes after seeding");
    }
}
