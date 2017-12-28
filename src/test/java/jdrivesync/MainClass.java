package jdrivesync;
import java.io.File;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Scanner;


public class MainClass
{
	public static int level = 0;
	
	public static void flattenDirectory(String directoryToDelete)
	{
		HashMap<String,BigInteger> nameMap = new HashMap<String,BigInteger>();
		File folder = new File(directoryToDelete);
		Boolean nestedDirectories = true;
		while(nestedDirectories)
		{
			nestedDirectories = false;
			File[] listFiles = folder.listFiles();
			for(File file : listFiles)
			{
				if(file.isDirectory())
				{
					System.out.println("Going into " + file.getName());
					File folderInside = new File(file.getAbsolutePath());
					File[] listFilesInside = folderInside.listFiles();
					for(File fileInside : listFilesInside)
					{
						if(fileInside.isDirectory())
						{
							nestedDirectories = true;
						}
						System.out.println("Moving file or directory " + fileInside.getName());
						String newName = fileInside.getName();
						if(nameMap.containsKey(newName))
						{
							BigInteger bigint = nameMap.get(newName);
							nameMap.put(newName, bigint.add(BigInteger.ONE));
							newName += bigint.toString();
						}
						else
						{
							nameMap.put(newName, new BigInteger("1"));
						}
						if(!fileInside.renameTo(new File(folder.getAbsoluteFile() + "\\" + newName)))
						{
							System.err.println("Couldn't move file or directory " + newName);
						}
					}
					System.out.println("Going back to " + folder.getName());
				}
			}
		}
		iterativeDelete(directoryToDelete);
	}
	
	public static void recursiveDelete(String directoryToDelete)
	{
		File folder = new File(directoryToDelete);
		File[] listFiles = folder.listFiles();
		for(File file : listFiles)
		{
			String newDir = file.getName();
			if(file.isDirectory())
			{
				System.out.println("Going into " + newDir + " (Level " + ++level + ")");
                recursiveDelete(folder.getAbsolutePath() + "\\" + newDir);
			}
			else if(file.isFile())
			{
				System.out.println("Deleting file " + file.getName());
				if(!file.delete())
				{
					System.err.println("Couldn't delete file " + file.getName());
				}
			}
			else
			{
				System.err.println("Unknown type found");
			}
        }
		if(level != 0)
		{
			System.out.println("Deleting folder " + directoryToDelete);
			if(!folder.delete())
			{
				System.err.println("Couldn't delete folder " + directoryToDelete);
			}
			int lastSlash = folder.getAbsolutePath().lastIndexOf("\\");
			String path = directoryToDelete.substring(0, lastSlash);
			lastSlash = path.lastIndexOf("\\");
			String lastDir = path.substring(lastSlash+1);
			System.out.println("Going back to " + lastDir + " (Level " + --level + ")");
		}
		else
		{
			System.out.println("Deleting FINAL FOLDER " + directoryToDelete);
			if(!folder.delete())
			{
				System.err.println("Couldn't delete folder " + directoryToDelete);
			}
			System.out.println("SUCCESS!");
		}
	}

	public static void iterativeDelete(String directoryToDelete)
	{
		File folder = new File(directoryToDelete);
		while(true)
		{
			String newDir = "";
			File[] listFiles = folder.listFiles();
			for(File file : listFiles)
			{
				if(file.isFile())
				{
					System.out.println("Deleting file " + file.getName());
					if(!file.delete())
					{
						System.err.println("Couldn't delete file " + file.getName());
					}
				}
				else if(file.isDirectory())
				{
					newDir = file.getName();
					break;
				}
				else
				{
					System.err.println("Unknown type found");
				}
			}
			if(!newDir.equals(""))
			{
				System.out.println("Going into " + newDir + " (Level " + ++level + ")");
				folder = new File(folder.getAbsolutePath() + "\\" + newDir);
				continue;
			}
			if(folder.getAbsolutePath().equals(directoryToDelete))
			{
				break;
			}
			System.out.println("Deleting folder " + folder.getName());
			if(!folder.delete())
			{
				System.err.println("Couldn't delete folder " + folder.getName());
			}
			String path = folder.getAbsolutePath();
			int lastSlash = path.lastIndexOf("\\");
			path = path.substring(0, lastSlash);
			lastSlash = path.lastIndexOf("\\");
			String lastDir = path.substring(lastSlash+1);
			System.out.println("Going back to " + lastDir + " (Level " + --level + ")");
			folder = new File(path);
		}
		System.out.println("Deleting FINAL FOLDER " + folder.getName());
		if(!folder.delete())
		{
			System.err.println("Couldn't delete folder " + folder.getName());
		}
		System.out.println("SUCCESS!");
	}
	
	public static void main(String[] args)
	{
		System.out.print("Enter directory to delete: ");
		Scanner sc = new Scanner(System.in);
	    String directoryToDelete = sc.nextLine();
	    System.out.println("Method list:\n");
	    System.out.println("1. Iterative delete");
	    System.out.println("2. Recursive delete");
	    System.out.println("3. Directory flattening");
	    
	    int method = 0;
	    while(method > 3 || method < 1)
	    {
	    	System.out.print("Choose method: ");
	    	method = sc.nextInt();
	    }
	    sc.close();
	    switch(method)
	    {
	    	case 1:
	    		iterativeDelete(directoryToDelete);
	    	break;
	    	case 2:
	    		recursiveDelete(directoryToDelete);
	    	break;
	    	case 3:
	    		flattenDirectory(directoryToDelete);
	    	break;	
	    }
	}
}
