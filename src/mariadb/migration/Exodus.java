package mariadb.migration;

//Exodus Unified Main Code
public class Exodus {
    public static void main(String[] args) {
        String CommandLineParam = "mysql";

        if (args.length == 1) {
            CommandLineParam = args[0].toLowerCase();
        }

        switch (CommandLineParam) {
            case "mysql":
                System.out.println("-\nStarting MySQL Migration Job...");
                new mariadb.migration.mysql.MySQLMain();
                System.out.println("MySQL Migration Job Completed...\n");
                break;
            case "db2":
                System.out.println("\nExodus " + args[0] + " not ready yet!!!\n\n");
                break;
        case "oracle":
                System.out.println("\nExodus " + args[0] + " not ready yet!!!\n\n");
                break;
            default:
                System.out.println("\nUnknown Source Database!\nValid options: MySQL, DB2, ORACLE\n\n");
                break;
        }
        return;
    }
}
