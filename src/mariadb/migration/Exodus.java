package mariadb.migration;

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
            case "oracle":
                System.out.println("\n" + args[0] + " Code not ready yet!!!\n\n");
                break;
            default:
                System.out.println("\nUnknown Source Database!\nValid options: MySQL, DB2, ORACLE\n\n");
                break;
        }
        return;
    }
}
